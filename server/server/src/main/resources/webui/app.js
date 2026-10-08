// Small helpers on top of htmx: toasts, local time rendering, error reporting.
(function () {
  function toast(message, kind) {
    var box = document.getElementById('toasts');
    if (!box || !message) return;
    var el = document.createElement('div');
    el.className = 'toast' + (kind === 'bad' ? ' bad' : '');
    el.setAttribute('role', 'status');
    el.textContent = message;
    box.appendChild(el);
    setTimeout(function () { el.remove(); }, kind === 'bad' ? 7000 : 3500);
  }

  document.addEventListener('toast', function (e) {
    var d = e.detail || {};
    toast(d.message || d.value, d.kind);
  });

  document.body.addEventListener('htmx:responseError', function (e) {
    var xhr = e.detail.xhr;
    var status = xhr && xhr.status;
    if (status === 401) { window.location.href = '/ui/login'; return; }
    toast('Request failed (' + status + ').', 'bad');
  });
  document.body.addEventListener('htmx:sendError', function () {
    toast('Cannot reach the server.', 'bad');
  });

  var dateFmt = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  var rtf = typeof Intl.RelativeTimeFormat === 'function' ? new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' }) : null;

  function relative(date) {
    var diff = (date.getTime() - Date.now()) / 1000;
    var abs = Math.abs(diff);
    if (!rtf) return dateFmt.format(date);
    if (abs < 45) return rtf.format(Math.round(diff), 'second');
    if (abs < 2700) return rtf.format(Math.round(diff / 60), 'minute');
    if (abs < 79200) return rtf.format(Math.round(diff / 3600), 'hour');
    if (abs < 2592000) return rtf.format(Math.round(diff / 86400), 'day');
    return dateFmt.format(date);
  }

  function localize(root) {
    (root || document).querySelectorAll('time[datetime]').forEach(function (el) {
      var d = new Date(el.getAttribute('datetime'));
      if (isNaN(d.getTime())) return;
      el.textContent = el.dataset.fmt === 'relative' ? relative(d) : dateFmt.format(d);
      el.title = d.toLocaleString();
    });
  }

  document.addEventListener('DOMContentLoaded', function () { localize(document); });
  document.body.addEventListener('htmx:afterSwap', function (e) { localize(e.target); });
  document.body.addEventListener('htmx:load', function (e) { localize(e.target); });
  // refresh relative times
  setInterval(function () { localize(document); }, 60000);
})();
