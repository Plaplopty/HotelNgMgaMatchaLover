package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final UserRepository users;
    public ReservationService(ReservationRepository reservations, RoomRepository rooms, UserRepository users) {
        this.reservations = reservations; this.rooms = rooms; this.users = users;
    }

    public boolean isAvailable(Room room, LocalDate in, LocalDate out) {
        if (room == null || room.getId() == null || in == null || out == null ||
                !out.isAfter(in) || in.isBefore(LocalDate.now()) || !room.isAvailable() || !room.isActive()) return false;
        return reservations.findAll().stream().noneMatch(r ->
            r.getRoom().getId().equals(room.getId()) &&
            !List.of("CANCELLED", "REJECTED").contains(r.getStatus()) &&
            in.isBefore(r.getCheckOut()) && out.isAfter(r.getCheckIn()));
    }

    @Transactional
    public Reservation updateStatus(Long reservationId, String status) {
        if (status == null || !List.of("APPROVED", "REJECTED", "CANCELLED").contains(status))
            throw new IllegalArgumentException("Choose a valid reservation status.");
        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if ("APPROVED".equals(status)) {
            Room room = reservation.getRoom();
            // Ignore this reservation while checking whether approval conflicts with another booking.
            boolean conflict = reservations.findAll().stream().anyMatch(other ->
                !other.getId().equals(reservation.getId()) &&
                other.getRoom().getId().equals(room.getId()) &&
                !List.of("CANCELLED", "REJECTED").contains(other.getStatus()) &&
                reservation.getCheckIn().isBefore(other.getCheckOut()) &&
                reservation.getCheckOut().isAfter(other.getCheckIn()));
            if (conflict) throw new IllegalArgumentException("Cannot approve: this room overlaps another active reservation.");
        }
        reservation.setStatus(status);
        return reservations.save(reservation);
    }

    @Transactional
    public Reservation reserve(String email, Long roomId, LocalDate in, LocalDate out) {
        if (in == null || out == null || !out.isAfter(in) || in.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Choose a valid check-in date and a check-out date after it.");
        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow();
        Room room = rooms.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found."));
        if (!isAvailable(room, in, out)) throw new IllegalArgumentException("That room is not available for the selected dates.");
        return reservations.save(new Reservation(user, room, in, out));
    }
}
