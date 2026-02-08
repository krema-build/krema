package build.krema.core.demo;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import build.krema.core.Krema;
import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;

/**
 * Demo application showcasing Krema features including events.
 */
public class DemoApp {

    public static void main(String[] args) {
        AtomicReference<EventEmitter> emitterRef = new AtomicReference<>();
        DemoCommands commands = new DemoCommands(emitterRef);

        Krema.app()
            .title("Krema Demo")
            .size(1024, 768)
            .debug(true)
            .events(emitterRef::set)
            .html(getHtml())
            .commands(commands)
            .run();
    }

    private static String getHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Krema Demo</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        padding: 2rem;
                        background: #1a1a2e;
                        color: #eee;
                        min-height: 100vh;
                    }
                    h1 {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        margin-bottom: 1rem;
                    }
                    .card {
                        background: #16213e;
                        border-radius: 12px;
                        padding: 1.5rem;
                        margin-bottom: 1rem;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.3);
                    }
                    .card h2 { color: #667eea; margin-bottom: 1rem; font-size: 1.2rem; }
                    button {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border: none;
                        padding: 0.75rem 1.5rem;
                        border-radius: 8px;
                        cursor: pointer;
                        font-size: 1rem;
                        transition: transform 0.2s, box-shadow 0.2s;
                        margin-right: 0.5rem;
                        margin-bottom: 0.5rem;
                    }
                    button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
                    }
                    button:active { transform: translateY(0); }
                    input, select {
                        background: #0f3460;
                        border: 1px solid #667eea;
                        color: #eee;
                        padding: 0.75rem;
                        border-radius: 8px;
                        margin-right: 0.5rem;
                        margin-bottom: 0.5rem;
                        font-size: 1rem;
                    }
                    input:focus, select:focus {
                        outline: none;
                        border-color: #764ba2;
                    }
                    .result {
                        background: #0f3460;
                        padding: 1rem;
                        border-radius: 8px;
                        margin-top: 1rem;
                        font-family: 'SF Mono', Monaco, monospace;
                        white-space: pre-wrap;
                        border-left: 4px solid #667eea;
                    }
                    .success { border-left-color: #4ade80; }
                    .error { border-left-color: #f87171; }
                    .event { border-left-color: #fbbf24; }
                    .flex { display: flex; gap: 1rem; flex-wrap: wrap; }
                    .info { font-size: 0.9rem; color: #888; margin-top: 0.5rem; }
                </style>
            </head>
            <body>
                <h1>Krema Demo</h1>
                <p style="margin-bottom: 2rem; color: #888;">
                    Lightweight desktop apps with system webviews + Panama FFM
                </p>

                <div class="card">
                    <h2>Greeting Command</h2>
                    <div class="flex">
                        <input type="text" id="name" placeholder="Enter your name" value="World">
                        <button onclick="greet()">Greet</button>
                    </div>
                    <div id="greetResult" class="result" style="display:none;"></div>
                </div>

                <div class="card">
                    <h2>Calculator Command</h2>
                    <div class="flex">
                        <input type="number" id="num1" value="10" style="width: 80px;">
                        <select id="op">
                            <option value="add">+</option>
                            <option value="subtract">-</option>
                            <option value="multiply">\u00d7</option>
                            <option value="divide">\u00f7</option>
                        </select>
                        <input type="number" id="num2" value="5" style="width: 80px;">
                        <button onclick="calculate()">Calculate</button>
                    </div>
                    <div id="calcResult" class="result" style="display:none;"></div>
                </div>

                <div class="card">
                    <h2>System Info Command</h2>
                    <button onclick="getSystemInfo()">Get System Info</button>
                    <div id="sysResult" class="result" style="display:none;"></div>
                </div>

                <div class="card">
                    <h2>Events (Backend to Frontend)</h2>
                    <button onclick="startTimer()">Start Timer</button>
                    <button onclick="stopTimer()">Stop Timer</button>
                    <p class="info">Timer emits events from Java to JavaScript</p>
                    <div id="eventResult" class="result event" style="display:none;"></div>
                </div>

                <div class="card">
                    <h2>Echo Command (Error Handling)</h2>
                    <div class="flex">
                        <input type="text" id="echoMsg" placeholder="Enter message" value="Hello Krema!">
                        <button onclick="echo()">Echo</button>
                        <button onclick="echoError()">Trigger Error</button>
                    </div>
                    <div id="echoResult" class="result" style="display:none;"></div>
                </div>

                <p class="info" style="margin-top: 2rem;">
                    Open DevTools (right-click \u2192 Inspect) to see IPC messages in the console.
                </p>

                <script>
                    // Listen for timer events
                    window.krema.on('timer-tick', (data) => {
                        showResult('eventResult', `Timer tick: ${data.count} (${new Date().toLocaleTimeString()})`, true, 'event');
                    });

                    window.krema.on('timer-stopped', () => {
                        const el = document.getElementById('eventResult');
                        el.textContent += '\\nTimer stopped';
                    });

                    async function greet() {
                        const name = document.getElementById('name').value || 'World';
                        try {
                            const result = await window.krema.invoke('greet', { name });
                            showResult('greetResult', result, true);
                        } catch (e) {
                            showResult('greetResult', e.message, false);
                        }
                    }

                    async function calculate() {
                        const a = parseFloat(document.getElementById('num1').value) || 0;
                        const b = parseFloat(document.getElementById('num2').value) || 0;
                        const operation = document.getElementById('op').value;
                        try {
                            const result = await window.krema.invoke('calculate', { a, b, operation });
                            showResult('calcResult', `Result: ${result}`, true);
                        } catch (e) {
                            showResult('calcResult', e.message, false);
                        }
                    }

                    async function getSystemInfo() {
                        try {
                            const result = await window.krema.invoke('systemInfo', {});
                            showResult('sysResult', JSON.stringify(result, null, 2), true);
                        } catch (e) {
                            showResult('sysResult', e.message, false);
                        }
                    }

                    async function startTimer() {
                        try {
                            await window.krema.invoke('startTimer', {});
                            showResult('eventResult', 'Timer started...', true, 'event');
                        } catch (e) {
                            showResult('eventResult', e.message, false);
                        }
                    }

                    async function stopTimer() {
                        try {
                            await window.krema.invoke('stopTimer', {});
                        } catch (e) {
                            showResult('eventResult', e.message, false);
                        }
                    }

                    async function echo() {
                        const message = document.getElementById('echoMsg').value;
                        try {
                            const result = await window.krema.invoke('echo', { message });
                            showResult('echoResult', result, true);
                        } catch (e) {
                            showResult('echoResult', e.message, false);
                        }
                    }

                    async function echoError() {
                        try {
                            await window.krema.invoke('echo', { message: 'ERROR' });
                        } catch (e) {
                            showResult('echoResult', 'Caught error: ' + e.message, false);
                        }
                    }

                    function showResult(id, text, success, type) {
                        const el = document.getElementById(id);
                        el.style.display = 'block';
                        el.textContent = text;
                        el.className = 'result ' + (type || (success ? 'success' : 'error'));
                    }
                </script>
            </body>
            </html>
            """;
    }
}

/**
 * Demo commands showcasing @KremaCommand usage.
 */
class DemoCommands {
    private final AtomicReference<EventEmitter> emitterRef;
    private ScheduledExecutorService timerService;
    private int tickCount = 0;

    DemoCommands(AtomicReference<EventEmitter> emitterRef) {
        this.emitterRef = emitterRef;
    }

    @KremaCommand
    public String greet(String name) {
        return "Hello, " + name + "! Welcome to Krema.";
    }

    @KremaCommand
    public double calculate(double a, double b, String operation) {
        return switch (operation) {
            case "add" -> a + b;
            case "subtract" -> a - b;
            case "multiply" -> a * b;
            case "divide" -> {
                if (b == 0) throw new ArithmeticException("Division by zero");
                yield a / b;
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    @KremaCommand
    public SystemInfo systemInfo() {
        return new SystemInfo(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"),
            System.getProperty("java.version"),
            System.getProperty("java.vendor"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / 1024 / 1024
        );
    }

    @KremaCommand
    public String startTimer() {
        if (timerService != null && !timerService.isShutdown()) {
            return "Timer already running";
        }

        tickCount = 0;
        timerService = Executors.newSingleThreadScheduledExecutor();
        timerService.scheduleAtFixedRate(() -> {
            EventEmitter emitter = emitterRef.get();
            if (emitter != null) {
                tickCount++;
                emitter.emit("timer-tick", Map.of("count", tickCount));
            }
        }, 0, 1, TimeUnit.SECONDS);

        return "Timer started";
    }

    @KremaCommand
    public String stopTimer() {
        if (timerService != null) {
            timerService.shutdown();
            EventEmitter emitter = emitterRef.get();
            if (emitter != null) {
                emitter.emit("timer-stopped");
            }
        }
        return "Timer stopped";
    }

    @KremaCommand
    public String echo(String message) {
        if ("ERROR".equals(message)) {
            throw new RuntimeException("Intentional error for testing");
        }
        return "Echo: " + message;
    }

    record SystemInfo(
        String osName,
        String osVersion,
        String osArch,
        String javaVersion,
        String javaVendor,
        int processors,
        long maxMemoryMb
    ) {}
}
