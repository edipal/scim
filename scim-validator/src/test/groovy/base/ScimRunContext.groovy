package base

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ScimRunContext {

    private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>()
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<ScimHttpExchange>> EXCHANGES = new ConcurrentHashMap<>()

    private static volatile boolean captureEnabled = false

    static void reset() {
        CURRENT_TEST.remove()
        EXCHANGES.clear()
    }

    static void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled
    }

    static boolean isCaptureEnabled() {
        return captureEnabled
    }

    static void beginTest(String testId) {
        if (testId == null) {
            CURRENT_TEST.remove()
            return
        }
        CURRENT_TEST.set(testId)
        EXCHANGES.putIfAbsent(testId, new CopyOnWriteArrayList<>())
    }

    static void endTest() {
        CURRENT_TEST.remove()
    }

    static void record(ScimHttpExchange exchange) {
        if (!captureEnabled || exchange == null) {
            return
        }
        String testId = CURRENT_TEST.get()
        if (testId == null) {
            testId = "_unassigned"
        }
        EXCHANGES.computeIfAbsent(testId, k -> new CopyOnWriteArrayList<>()).add(exchange)
    }

    static List<ScimHttpExchange> getForTest(String testId) {
        if (testId == null) {
            return List.of()
        }
        return new ArrayList<>(EXCHANGES.getOrDefault(testId, new CopyOnWriteArrayList<>()))
    }
}
