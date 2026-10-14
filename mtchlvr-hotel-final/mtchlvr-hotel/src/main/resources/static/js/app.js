document.addEventListener("DOMContentLoaded", () => {
  const today = new Date();
  const localDate = new Date(today.getTime() - today.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  document.querySelectorAll('input[name="checkIn"]').forEach((input) => {
    input.min = localDate;
    input.addEventListener("change", () => {
      const form = input.closest("form");
      const out = form?.querySelector('input[name="checkOut"]');
      if (out) {
        out.min = input.value || localDate;
        if (out.value && out.value <= input.value) out.value = "";
      }
    });
  });
  document.querySelectorAll('input[name="checkOut"]').forEach((input) => {
    input.min = localDate;
  });
});

// Confirmation for important actions. Forms with an explicit onsubmit handler
// already provide their own confirmation and must not receive a second prompt.
document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("form").forEach((form) => {
    if (form.hasAttribute("onsubmit") || form.dataset.confirm === "false") return;
    const submit = form.querySelector('button[type="submit"]');
    const label = (submit?.textContent || "").trim().toLowerCase();
    let message = null;
    if (label.includes("delete")) message = "Are you sure you want to delete this item? This action may not be reversible.";
    else if (label.includes("approve")) message = "Are you sure you want to approve this reservation?";
    else if (label.includes("reject")) message = "Are you sure you want to reject this reservation?";
    else if (label.includes("cancel")) message = "Are you sure you want to continue with cancellation?";
    if (message) form.addEventListener("submit", (event) => {
      if (!window.confirm(message)) event.preventDefault();
    });
  });
});
