import com.mirage.common.ScopeGuard;
import com.mirage.specter.SpecterConsole;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class SpecterShot {
    public static void main(String[] args) throws Exception {
        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24", "127.0.0.1/32"));
        SpecterConsole console = new SpecterConsole(guard);

        Field cb = SpecterConsole.class.getDeclaredField("campaignBox");
        cb.setAccessible(true);
        @SuppressWarnings("unchecked")
        JComboBox<String> box = (JComboBox<String>) cb.get(console);

        Method onLaunch = SpecterConsole.class.getDeclaredMethod("onLaunch");
        onLaunch.setAccessible(true);

        SwingUtilities.invokeAndWait(() -> {
            console.setVisible(true);
            box.setSelectedItem("Full campaign");
        });
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> {
            try { onLaunch.invoke(console); } catch (Exception e) { e.printStackTrace(); }
        });
        Thread.sleep(4000);     // let the campaign run and append log lines
        Thread.sleep(120000);   // hold the window open for the screenshot
    }
}
