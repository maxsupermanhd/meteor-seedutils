package flexcoral.seedutils;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;

import java.util.List;

public class LongsViewScreen extends WindowScreen {

    private final List<Long> vals;

    public LongsViewScreen(GuiTheme theme, String title, List<Long> v) {
        super(theme, title);
        vals = v;
    }

    @Override
    public void initWidgets() {
        add(theme.label(String.format("Showing %d entries", vals.size())));
        var t = add(theme.table()).widget();

        for (long seed : vals) {
            t.add(theme.label(String.valueOf(seed))).expandX();
            var copyBtn = t.add(theme.button("Copy")).widget();
            copyBtn.action = () -> {
                if (client != null) {
                    client.keyboard.setClipboard(String.valueOf(seed));
                    copyBtn.set("Copied");
                }
            };
            t.row();
        }

    }
}
