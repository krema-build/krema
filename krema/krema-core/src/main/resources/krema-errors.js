/**
 * Krema WebView Error Capture
 * Reports unhandled JS errors and unhandled Promise rejections to the Java backend.
 */
(function() {
    'use strict';

    window.onerror = function(message, source, lineno, colno, error) {
        var stack = error && error.stack ? error.stack : '';
        if (typeof __krema_report_error === 'function') {
            __krema_report_error(JSON.stringify({
                message: String(message),
                source: source || '',
                lineno: lineno || 0,
                stack: stack
            }));
        }
    };

    window.addEventListener('unhandledrejection', function(event) {
        var reason = event.reason;
        var message = reason instanceof Error ? reason.message : String(reason);
        var stack = reason instanceof Error ? reason.stack : '';
        if (typeof __krema_report_error === 'function') {
            __krema_report_error(JSON.stringify({
                message: 'Unhandled Promise rejection: ' + message,
                source: '',
                lineno: 0,
                stack: stack || ''
            }));
        }
    });
})();
