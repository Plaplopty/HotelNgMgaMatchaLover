package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import com.mtchlvr.hotel.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import jakarta.servlet.http.HttpSession;

@Controller
public class HotelController {
    private final RoomRepository rooms;
    private final UserRepository users;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final CaptchaService captcha;
    private final PasswordEncoder encoder;
    private final ReportRepository reports;

    public HotelController(RoomRepository rooms, UserRepository users, ReservationRepository reservations,
                           ReservationService reservationService, CaptchaService captcha, PasswordEncoder encoder, ReportRepository reports) {
        this.rooms = rooms; this.users = users; this.reservations = reservations;
        this.reservationService = reservationService; this.captcha = captcha; this.encoder = encoder; this.reports = reports;
    }

    @GetMapping("/") public String home(Model model) {
        model.addAttribute("rooms", rooms.findByActiveTrueAndAvailableTrueOrderByTypeAscRoomNumberAsc());
        return "index";
    }
    @GetMapping("/rooms") public String browse(Model model) {
        model.addAttribute("rooms", rooms.findByActiveTrueOrderByTypeAscRoomNumberAsc()); return "rooms";
    }
    @GetMapping("/login") public String login(Model model) { return "login"; }

    @GetMapping("/register") public String register(Model model, HttpSession session) {
        model.addAttribute("form", new RegisterForm()); return "register";
    }
    @PostMapping("/register") public String registerPost(@Valid @ModelAttribute("form") RegisterForm form,
            BindingResult errors, HttpSession session, Model model, RedirectAttributes flash,
            @RequestParam(name = "g-recaptcha-response", required = false) String captchaToken,
            jakarta.servlet.http.HttpServletRequest request) {
        if (!captcha.verify(captchaToken, request.getRemoteAddr())) errors.reject("captcha", "Please complete the Google reCAPTCHA check and try again.");
        if (!java.util.Objects.equals(form.password, form.confirmPassword)) {
            errors.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }
        String normalizedEmail = form.email == null ? "" : form.email.trim();
        if (!normalizedEmail.isBlank() && users.existsByEmailIgnoreCase(normalizedEmail)) {
            errors.rejectValue("email", "duplicate", "An account with this email already exists.");
        }
        if (errors.hasErrors()) return "register";
        users.save(new AppUser(normalizedEmail.toLowerCase(java.util.Locale.ROOT), encoder.encode(form.password),
                form.fullName.trim(), "CUSTOMER", true));
        flash.addFlashAttribute("success", "Account created. You can now sign in.");
        return "redirect:/login";
    }
    @GetMapping("/customer/reservations") public String myReservations(Authentication auth, Model model) {
        model.addAttribute("reservations", reservations.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(auth.getName()));
        return "my-reservations";
    }
    @PostMapping("/customer/reservations") public String makeReservation(Authentication auth,
            @RequestParam Long roomId, @RequestParam String checkIn, @RequestParam String checkOut,
            RedirectAttributes flash) {
        try {
            reservationService.reserve(auth.getName(), roomId, LocalDate.parse(checkIn), LocalDate.parse(checkOut));
            flash.addFlashAttribute("success", "Reservation request submitted.");
        } catch (Exception ex) {
            flash.addFlashAttribute("error", ex.getMessage() == null ? "Could not create reservation." : ex.getMessage());
        }
        return "redirect:/customer/reservations";
    }
    @PostMapping("/customer/reservations/{id}/cancel") public String cancel(@PathVariable Long id,
            Authentication auth, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || !r.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Reservation not found."); return "redirect:/customer/reservations";
        }
        if (!r.getStatus().equals("PENDING") && !r.getStatus().equals("APPROVED")) {
            flash.addFlashAttribute("error", "This reservation cannot be cancelled in its current status.");
        } else {
            r.setStatus("CANCELLATION_REQUESTED"); reservations.save(r);
            flash.addFlashAttribute("success", "Cancellation request sent to the manager.");
        }
        return "redirect:/customer/reservations";
    }

    @GetMapping("/manager") public String manager(Model model) {
        model.addAttribute("rooms", rooms.findAll());
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc());
        model.addAttribute("reports", reports.findAllByOrderByCreatedAtDesc());
        return "manager";
    }
    @PostMapping("/customer/reports") public String submitReport(Authentication auth,
            @RequestParam String category, @RequestParam String message, RedirectAttributes flash) {
        String clean = message == null ? "" : message.trim();
        if (clean.isBlank() || clean.length() > 2000 || category == null ||
                !java.util.List.of("SERVICE", "ROOM", "BOOKING", "WEBSITE", "OTHER").contains(category)) {
            flash.addFlashAttribute("error", "Choose a report category and enter a message of up to 2,000 characters.");
            return "redirect:/customer/reservations#report";
        }
        AppUser customer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        reports.save(new GuestReport(customer, category, clean));
        flash.addFlashAttribute("success", "Thank you. Your report was sent to the hotel manager.");
        return "redirect:/customer/reservations#report";
    }

    @GetMapping("/manager/reports") public String managerReports(Model model) {
        model.addAttribute("reports", reports.findAllByOrderByCreatedAtDesc());
        return "reports";
    }

    @PostMapping("/manager/reports/{id}/status") public String updateReportStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp="OPEN|RESOLVED") String status, RedirectAttributes flash) {
        GuestReport report = reports.findById(id).orElse(null);
        if (report == null) flash.addFlashAttribute("error", "Report not found.");
        else { report.setStatus(status); reports.save(report); flash.addFlashAttribute("success", "Report status updated."); }
        return "redirect:/manager/reports";
    }

    @PostMapping("/manager/rooms/{id}/deactivate") public String deactivateRoom(@PathVariable Long id, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) {
            flash.addFlashAttribute("error", "Room not found.");
        } else if (!room.isActive()) {
            flash.addFlashAttribute("error", "This room is already inactive.");
        } else {
            room.setActive(false);
            rooms.save(room);
            flash.addFlashAttribute("success", "Room " + room.getRoomNumber() + " was deactivated and hidden from new bookings.");
        }
        return "redirect:/manager";
    }

    @PostMapping("/manager/rooms/{id}/reactivate") public String reactivateRoom(@PathVariable Long id, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) {
            flash.addFlashAttribute("error", "Room not found.");
        } else if (room.isActive()) {
            flash.addFlashAttribute("error", "This room is already active.");
        } else {
            room.setActive(true);
            rooms.save(room);
            flash.addFlashAttribute("success", "Room " + room.getRoomNumber() + " was reactivated.");
        }
        return "redirect:/manager";
    }

    @PostMapping("/manager/reservations/{id}/status") public String updateStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp="APPROVED|REJECTED|CANCELLED") String status, RedirectAttributes flash) {
        try {
            reservationService.updateStatus(id, status);
            flash.addFlashAttribute("success", "Reservation status updated.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager";
    }


public static class RegisterForm {
    @NotBlank @Size(min=8, max=80)
    private String fullName = "";

    @NotBlank @Email @Size(max=160)
    private String email = "";

    @NotBlank
    @Size(min=10, max=70)
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$",
        message = "Password must have uppercase and lowercase letters, a number, and a special symbol, with no spaces."
    )
    private String password = "";

    @NotBlank
    private String confirmPassword = "";

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}

}
