/**
 * Krema IPC Bridge
 * Provides window.krema.invoke() and window.krema.on() API for frontend-backend communication.
 */
(function() {
    'use strict';

    // Event listeners for backend-to-frontend events
    const listeners = new Map();

    /**
     * Invoke a backend command.
     * @param {string} command - The command name
     * @param {object} args - The command arguments
     * @returns {Promise<any>} - Promise resolving to the command result
     */
    function invoke(command, args) {
        if (args === undefined) args = {};
        const request = JSON.stringify({ cmd: command, args: args });

        // Call the native function (bound by Java)
        // The webview library makes bound functions return Promises automatically
        // and parses the JSON result for us
        if (typeof __krema_invoke === 'function') {
            return __krema_invoke(request);
        } else {
            return Promise.reject(new Error('Krema bridge not initialized'));
        }
    }

    /**
     * Register an event listener for backend events.
     * @param {string} event - The event name
     * @param {function} callback - The callback function
     * @returns {function} - Unsubscribe function
     */
    function on(event, callback) {
        if (!listeners.has(event)) {
            listeners.set(event, new Set());
        }
        listeners.get(event).add(callback);

        // Return unsubscribe function
        return function unsubscribe() {
            const eventListeners = listeners.get(event);
            if (eventListeners) {
                eventListeners.delete(callback);
                if (eventListeners.size === 0) {
                    listeners.delete(event);
                }
            }
        };
    }

    /**
     * Called by Java to emit an event to the frontend.
     * @param {string} event - The event name
     * @param {string} dataJson - The event data as JSON
     */
    function __krema_emit(event, dataJson) {
        const eventListeners = listeners.get(event);
        if (!eventListeners || eventListeners.size === 0) {
            return;
        }

        try {
            const data = JSON.parse(dataJson);
            eventListeners.forEach(callback => {
                try {
                    callback(data);
                } catch (e) {
                    console.error('[Krema] Error in event listener:', e);
                }
            });
        } catch (e) {
            console.error('[Krema] Failed to parse event data:', e);
        }
    }

    // Expose the API
    window.krema = {
        invoke: invoke,
        on: on
    };

    // Expose internal callback for Java events
    window.__krema_emit = __krema_emit;

    console.log('[Krema] Bridge initialized');
})();
