/**
 * Krema Drag & Drop Handler
 * Provides HTML5-based drag-drop functionality with events emitted to the backend.
 */
(function() {
    'use strict';

    let config = {
        enabled: true,
        acceptedExtensions: null,
        acceptedMimeTypes: null,
        dropZoneSelector: null
    };

    let dropZone = null;

    /**
     * Configure drag-drop behavior.
     */
    function configure(options) {
        if (options.enabled !== undefined) config.enabled = options.enabled;
        if (options.acceptedExtensions !== undefined) config.acceptedExtensions = options.acceptedExtensions;
        if (options.acceptedMimeTypes !== undefined) config.acceptedMimeTypes = options.acceptedMimeTypes;
        if (options.dropZoneSelector !== undefined) {
            config.dropZoneSelector = options.dropZoneSelector;
            setupDropZone();
        }
    }

    /**
     * Enable drag-drop.
     */
    function enable() {
        config.enabled = true;
    }

    /**
     * Disable drag-drop.
     */
    function disable() {
        config.enabled = false;
    }

    /**
     * Get the drop zone element.
     */
    function getDropZone() {
        if (config.dropZoneSelector) {
            return document.querySelector(config.dropZoneSelector);
        }
        return document.body;
    }

    /**
     * Set up the drop zone with event listeners.
     */
    function setupDropZone() {
        if (dropZone) {
            removeListeners(dropZone);
        }
        dropZone = getDropZone();
        if (dropZone) {
            addListeners(dropZone);
        }
    }

    /**
     * Add drag-drop event listeners to an element.
     */
    function addListeners(element) {
        element.addEventListener('dragenter', handleDragEnter, false);
        element.addEventListener('dragover', handleDragOver, false);
        element.addEventListener('dragleave', handleDragLeave, false);
        element.addEventListener('drop', handleDrop, false);
    }

    /**
     * Remove drag-drop event listeners from an element.
     */
    function removeListeners(element) {
        element.removeEventListener('dragenter', handleDragEnter, false);
        element.removeEventListener('dragover', handleDragOver, false);
        element.removeEventListener('dragleave', handleDragLeave, false);
        element.removeEventListener('drop', handleDrop, false);
    }

    /**
     * Handle drag enter event.
     */
    function handleDragEnter(e) {
        if (!config.enabled) return;
        e.preventDefault();
        e.stopPropagation();

        emitEvent('file-drop-hover', {
            type: 'enter',
            x: e.clientX,
            y: e.clientY
        });
    }

    /**
     * Handle drag over event.
     */
    function handleDragOver(e) {
        if (!config.enabled) return;
        e.preventDefault();
        e.stopPropagation();

        // Set the drop effect
        if (e.dataTransfer) {
            e.dataTransfer.dropEffect = 'copy';
        }
    }

    /**
     * Handle drag leave event.
     */
    function handleDragLeave(e) {
        if (!config.enabled) return;
        e.preventDefault();
        e.stopPropagation();

        emitEvent('file-drop-hover', {
            type: 'leave',
            x: e.clientX,
            y: e.clientY
        });
    }

    /**
     * Handle drop event.
     */
    function handleDrop(e) {
        if (!config.enabled) return;
        e.preventDefault();
        e.stopPropagation();

        const files = extractFiles(e);
        if (files.length === 0) return;

        const filteredFiles = filterFiles(files);
        if (filteredFiles.length === 0) return;

        emitEvent('file-drop', {
            type: 'drop',
            files: filteredFiles,
            x: e.clientX,
            y: e.clientY
        });
    }

    /**
     * Extract file information from a drop event.
     */
    function extractFiles(e) {
        const files = [];

        if (e.dataTransfer && e.dataTransfer.files) {
            for (let i = 0; i < e.dataTransfer.files.length; i++) {
                const file = e.dataTransfer.files[i];
                files.push({
                    name: file.name,
                    path: file.path || null, // path may not be available in browser context
                    type: file.type || getMimeType(file.name),
                    size: file.size
                });
            }
        }

        return files;
    }

    /**
     * Filter files based on configuration.
     */
    function filterFiles(files) {
        return files.filter(file => {
            // Check extensions
            if (config.acceptedExtensions && config.acceptedExtensions.length > 0) {
                const ext = getExtension(file.name);
                if (!config.acceptedExtensions.includes(ext)) {
                    return false;
                }
            }

            // Check mime types
            if (config.acceptedMimeTypes && config.acceptedMimeTypes.length > 0) {
                if (!config.acceptedMimeTypes.includes(file.type)) {
                    return false;
                }
            }

            return true;
        });
    }

    /**
     * Get file extension from filename.
     */
    function getExtension(filename) {
        const idx = filename.lastIndexOf('.');
        if (idx === -1) return '';
        return filename.substring(idx + 1).toLowerCase();
    }

    /**
     * Get mime type from filename (basic implementation).
     */
    function getMimeType(filename) {
        const ext = getExtension(filename);
        const mimeTypes = {
            'txt': 'text/plain',
            'html': 'text/html',
            'css': 'text/css',
            'js': 'application/javascript',
            'json': 'application/json',
            'xml': 'application/xml',
            'pdf': 'application/pdf',
            'png': 'image/png',
            'jpg': 'image/jpeg',
            'jpeg': 'image/jpeg',
            'gif': 'image/gif',
            'svg': 'image/svg+xml',
            'mp3': 'audio/mpeg',
            'mp4': 'video/mp4',
            'zip': 'application/zip'
        };
        return mimeTypes[ext] || 'application/octet-stream';
    }

    /**
     * Emit an event to the frontend listeners (and backend via bridge).
     */
    function emitEvent(name, payload) {
        // Emit to frontend via krema bridge
        if (window.__krema_emit) {
            const dataJson = JSON.stringify({
                payload: payload,
                timestamp: Date.now()
            });
            window.__krema_emit(name, dataJson);
        }
    }

    // Initialize on DOM ready
    function init() {
        setupDropZone();
        console.log('[Krema] Drag-drop handler initialized');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Expose API for configuration
    window.__krema_dragdrop = {
        configure: configure,
        enable: enable,
        disable: disable
    };
})();
