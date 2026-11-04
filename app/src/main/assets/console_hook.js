(function() {
    'use strict';

    if (window.__AternosBridgeInjected) {
        console.log("[AternosBridge] Hook already active.");
        return;
    }
    window.__AternosBridgeInjected = true;

    // 1. Keep-Alive & Anti-Throttling: prevent browser from pausing WebSockets/timers in background
    try {
        document.hasFocus = function() { return true; };
        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
    } catch(e) {}

    const CONFIG = {
        logSelectors: [
            '#terminal',
            '.console',
            '.terminal-container',
            '.log-entries',
            '#terminal-entries',
            '.server-logs',
            '.server-body',
            '.console-content',
            '.console-logs',
            '.log',
            '#log',
            '.terminal'
        ].join(', '),
        statusSelectors: [
            '.server-status',
            '.status-label',
            '#server-status',
            '.server-status-label',
            '.navigation-status',
            '.server-header-status'
        ].join(', '),
        cfSelectors: [
            'iframe[src*="challenges.cloudflare.com"]',
            '#cf-challenge',
            '.cf-turnstile-wrapper',
            '#cf-turnstile-response'
        ].join(', '),
        defaultTriggerPrefix: '@server',
        statusPollIntervalMs: 2500,
        cfCheckIntervalMs: 1500,
        recheckIntervalMs: 3000
    };

    let targetNode = null;
    let observer = null;
    let lastKnownStatus = "";
    let isCfDetected = false;

    function safeBridgeCall(fn) {
        try {
            if (window.AndroidBridge) {
                fn(window.AndroidBridge);
            }
        } catch (e) {
            console.error("[AternosBridge] Bridge call error:", e);
        }
    }

    function checkCloudflareChallenge() {
        const cfElement = document.querySelector(CONFIG.cfSelectors);
        if (cfElement && !isCfDetected) {
            isCfDetected = true;
            console.warn("[AternosBridge] Cloudflare challenge detected!");
            safeBridgeCall(b => b.onCloudflareDetected());
        } else if (!cfElement && isCfDetected) {
            isCfDetected = false;
        }
    }

    function pollServerStatus() {
        const statusEl = document.querySelector(CONFIG.statusSelectors);
        if (statusEl) {
            const currentStatus = (statusEl.textContent || "").trim();
            if (currentStatus && currentStatus !== lastKnownStatus) {
                lastKnownStatus = currentStatus;
                console.log("[AternosBridge] Server status updated: " + currentStatus);
                safeBridgeCall(b => b.onServerStatusChange(currentStatus));
            }
        }
    }

    function parseLogLine(rawText) {
        if (!rawText || typeof rawText !== 'string') return;
        const text = rawText.trim();
        if (!text) return;

        // Stream raw log to native HUD
        safeBridgeCall(b => b.onConsoleLog(text, "INFO"));

        // Match chat triggers:
        // Pattern 1: [Server thread/INFO]: <GamerBhoid> @server give diamond 64
        // Pattern 2: [Async Chat Thread - #0/INFO]: <GamerBhoid> !ai time set day
        // Pattern 3: <GamerBhoid> @server time set day
        // Pattern 4: GamerBhoid: @server weather clear
        const chatRegex1 = /<([a-zA-Z0-9_]{1,16})>\s*(?:@server|!ai)\s+([\s\S]+)/i;
        const chatRegex2 = /(?:\[[^\]]*\]\s*:\s*)?<([a-zA-Z0-9_]{1,16})>\s*(?:@server|!ai)\s+([\s\S]+)/i;
        const chatRegex3 = /([a-zA-Z0-9_]{1,16})\s*:\s*(?:@server|!ai)\s+([\s\S]+)/i;

        let match = text.match(chatRegex1) || text.match(chatRegex2) || text.match(chatRegex3);
        if (match) {
            const sender = match[1].trim();
            const prompt = match[2].trim();
            if (sender && prompt) {
                console.log("[AternosBridge] Captured trigger from " + sender + ": " + prompt);
                safeBridgeCall(b => b.onPlayerPrompt(sender, prompt));
            }
        }
    }

    function attachObserver(node) {
        if (!node) return;
        if (observer) {
            observer.disconnect();
        }
        observer = new MutationObserver((mutations) => {
            for (let mutation of mutations) {
                mutation.addedNodes.forEach(addedNode => {
                    if (addedNode.nodeType === Node.TEXT_NODE) {
                        parseLogLine(addedNode.textContent);
                    } else if (addedNode.nodeType === Node.ELEMENT_NODE) {
                        parseLogLine(addedNode.textContent);
                    }
                });
            }
        });

        observer.observe(node, { childList: true, subtree: true });
        console.log("[AternosBridge] MutationObserver attached to log container.");
        safeBridgeCall(b => b.onBridgeReady());
    }

    function ensureObserverAttached() {
        const currentNode = document.querySelector(CONFIG.logSelectors);
        if (currentNode && currentNode !== targetNode) {
            targetNode = currentNode;
            attachObserver(targetNode);
        }
    }

    function handleAdblockOverlay() {
        try {
            window.canRunAds = true;
            window.isAdBlocked = false;
            window.isAdBlockActive = false;
            window.adsbygoogle = window.adsbygoogle || [];

            const continueButtons = document.querySelectorAll('.ad-replacement-btn, .btn-danger, .continue-btn, .btn-main, button, a.btn');
            continueButtons.forEach(btn => {
                const text = (btn.textContent || '').trim().toLowerCase();
                if (text.includes('continue') || text.includes('proceed') || text.includes('okay')) {
                    if (!btn.disabled) {
                        btn.click();
                    }
                }
            });

            const adOverlays = document.querySelectorAll('.ad-replacement, .ad-replacement-wrapper, .adblock-overlay, .ad-screen, #adblock-overlay, .modal-danger');
            adOverlays.forEach(el => {
                el.style.display = 'none';
            });

            if (document.body) {
                document.body.style.overflow = 'auto';
                document.body.style.filter = 'none';
            }
            if (document.documentElement) {
                document.documentElement.style.overflow = 'auto';
            }
        } catch(e) {}
    }

    // SPA Navigation handling (Aternos uses pushState / replaceState)
    function setupSpaNavigationHooks() {
        try {
            const originalPushState = history.pushState;
            const originalReplaceState = history.replaceState;

            history.pushState = function() {
                originalPushState.apply(this, arguments);
                setTimeout(ensureObserverAttached, 500);
            };

            history.replaceState = function() {
                originalReplaceState.apply(this, arguments);
                setTimeout(ensureObserverAttached, 500);
            };

            window.addEventListener('popstate', function() {
                setTimeout(ensureObserverAttached, 500);
            });
        } catch(e) {}
    }

    function initialize() {
        console.log("[AternosBridge] Initializing DOM hook...");

        setupSpaNavigationHooks();

        // Start Cloudflare, status polling, observer check, and adblock suppressor
        setInterval(checkCloudflareChallenge, CONFIG.cfCheckIntervalMs);
        setInterval(pollServerStatus, CONFIG.statusPollIntervalMs);
        setInterval(handleAdblockOverlay, 500);
        setInterval(ensureObserverAttached, CONFIG.recheckIntervalMs);

        // Initial checks
        checkCloudflareChallenge();
        pollServerStatus();
        handleAdblockOverlay();

        targetNode = document.querySelector(CONFIG.logSelectors);
        if (targetNode) {
            attachObserver(targetNode);
        } else {
            console.log("[AternosBridge] Target log container not found yet, watching document.body...");
            const docObserver = new MutationObserver(() => {
                targetNode = document.querySelector(CONFIG.logSelectors);
                if (targetNode) {
                    docObserver.disconnect();
                    attachObserver(targetNode);
                }
            });
            if (document.body) {
                docObserver.observe(document.body, { childList: true, subtree: true });
            } else {
                window.addEventListener('DOMContentLoaded', () => {
                    docObserver.observe(document.body, { childList: true, subtree: true });
                });
            }

            setTimeout(() => {
                if (!targetNode) {
                    console.warn("[AternosBridge] Diagnostic: Log container not attached within initial timeout.");
                    safeBridgeCall(b => b.onDiagnostic("DOM hook log container not found yet. Current URL: " + window.location.href));
                }
            }, 8000);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
})();
