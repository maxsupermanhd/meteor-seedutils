package flexcoral.seedutils.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class StringPickScreen<T> extends WindowScreen {
    private final GuiTheme theme;
    private final SelectCallback<T> callback;
    private String filterText = "";
    private List<T> options;
    private Stringer<T> stringer;

    public StringPickScreen(GuiTheme theme, String title, List<T> options, SelectCallback<T> callback) {
        super(theme, title);
        this.theme = theme;
        this.callback = callback;
        this.options = new ArrayList<>(options);
    }

    public StringPickScreen(GuiTheme theme, String title, List<T> options, SelectCallback<T> callback, Stringer<T> stringer) {
        super(theme, title);
        this.theme = theme;
        this.callback = callback;
        this.options = new ArrayList<>(options);
        this.stringer = stringer;
    }

    public interface SelectCallback<T> {
        void selection(T selected);
    }

    public interface Stringer<T> {
        String toString(T element);
    }

    @Override
    public void initWidgets() {
        WTable table = theme.table();
        table.minWidth = 400;

        WTextBox filter = add(theme.textBox(filterText, "Search")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.setCursorMax();
        filter.action = () -> {
            filterText = filter.get().trim();
            table.clear();
            fillTable(table);
        };

        add(table);
        fillTable(table);
    }

    private void fillTable(WTable table) {
        options.sort(Comparator.comparing(Object::toString));
        for (T option : options) {
            table.add(theme.label(stringer != null ? stringer.toString(option) : option.toString())).expandX();
            WButton b = table.add(theme.button("Select")).widget();
            b.action = () -> callback.selection(option);
        }
    }
}
