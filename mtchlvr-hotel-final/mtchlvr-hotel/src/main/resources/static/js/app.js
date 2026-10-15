document.addEventListener("DOMContentLoaded", () => {
  const today = new Date();
  const localDate = new Date(today.getTime() - today.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  document.querySelectorAll('input[name="checkIn"]').forEach((input) => {
    input.min = localDate;
    input.addEventListener("change", () => {
      const out = input.closest("form")?.querySelector('input[name="checkOut"]');
      if (out) { out.min = input.value || localDate; if (out.value && out.value <= input.value) out.value = ""; }
    });
  });
  document.querySelectorAll('input[name="checkOut"]').forEach((input) => input.min = localDate);

  document.querySelectorAll("[data-password-toggle]").forEach((button) => {
    button.addEventListener("click", () => {
      const input = document.getElementById(button.dataset.passwordToggle);
      if (!input) return;
      const visible = input.type === "password";
      input.type = visible ? "text" : "password";
      button.textContent = visible ? "Hide" : "Show";
      button.setAttribute("aria-label", visible ? "Hide password" : "Show password");
      button.setAttribute("aria-pressed", String(visible));
    });
  });

  const modal = document.getElementById("confirm-modal");
  if (modal) {
    const message = modal.querySelector("[data-confirm-message]");
    const cancel = modal.querySelector("[data-confirm-cancel]");
    const proceed = modal.querySelector("[data-confirm-proceed]");
    let pendingForm = null;
    document.querySelectorAll("form[data-confirm]").forEach(form => form.addEventListener("submit", event => {
      if (form.dataset.confirmed === "true") { form.dataset.confirmed = "false"; return; }
      event.preventDefault();
      pendingForm = form;
      message.textContent = form.dataset.confirm || "Are you sure you want to continue?";
      modal.hidden = false;
      cancel.focus();
    }));
    const close = () => { modal.hidden = true; pendingForm = null; };
    cancel.addEventListener("click", close);
    modal.addEventListener("click", event => { if (event.target === modal) close(); });
    proceed.addEventListener("click", () => {
      if (!pendingForm) return;
      const form = pendingForm;
      close();
      form.dataset.confirmed = "true";
      form.requestSubmit();
    });
    document.addEventListener("keydown", event => { if (event.key === "Escape" && !modal.hidden) close(); });
  }
});