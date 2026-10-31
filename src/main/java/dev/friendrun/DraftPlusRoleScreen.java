package dev.friendrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-player Draft+ role picker. */
public final class DraftPlusRoleScreen extends Screen {
    private final Screen parent;
    private Ui.R panel;
    private final Ui.R[] roleRects = new Ui.R[DraftPlusRole.values().length];
    private final Button[] roleButtons = new Button[DraftPlusRole.values().length];
    private Ui.R backRect;
    private Button backButton;

    public DraftPlusRoleScreen(Screen parent) {
        super(Component.literal("FriendRun — Роль Draft+"));
        this.parent = parent;
    }

    private Button make(Ui.R rect, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
            .bounds(rect.x(), rect.y(), rect.w(), rect.h()).build());
        Ui.invisible(button);
        return button;
    }

    private static String myRoleId(RunState state) {
        String me = FriendRunClient.myName();
        return state.players.stream().filter(player -> player.name().equals(me))
            .map(RunState.PlayerInfo::draftPlusRole).findFirst().orElse("");
    }

    private boolean editable() {
        RunState state = FriendRunClient.STATE;
        return FriendRunClient.inLobby() && state.gameMode == Game.DRAFT_PLUS
            && !state.launched && state.phase == RunState.Phase.IDLE;
    }

    @Override
    protected void init() {
        int widthPanel = Math.min(width - 20, 460);
        int heightPanel = Math.min(height - 20, 270);
        int x = (width - widthPanel) / 2;
        int y = (height - heightPanel) / 2;
        panel = new Ui.R(x, y, widthPanel, heightPanel);

        int gap = 8;
        int colWidth = (widthPanel - 32 - gap) / 2;
        for (int i = 0; i < DraftPlusRole.values().length; i++) {
            int col = i % 2;
            int row = i / 2;
            Ui.R rect = new Ui.R(x + 12 + col * (colWidth + gap), y + 58 + row * 48, colWidth, 40);
            roleRects[i] = rect;
            DraftPlusRole role = DraftPlusRole.values()[i];
            roleButtons[i] = make(rect, () -> {
                if (!editable()) return;
                FriendRunClient.setDraftPlusRole(role.id());
                Mc.setScreen(parent);
            });
        }

        backRect = new Ui.R(x + 12, y + heightPanel - 30, widthPanel - 24, 20);
        backButton = make(backRect, () -> Mc.setScreen(parent));
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        RunState state = FriendRunClient.STATE;
        String selectedId = myRoleId(state);
        boolean canChoose = editable();

        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.GEAR, panel.x() + 14, panel.y() + 12, 2, Ui.GOLD);
        Ui.bold(g, "Выбор роли Draft+", panel.x() + 36, panel.y() + 11, Ui.GOLD);
        Gfx.centered(g, canChoose ? "Выберите одну пассивную роль" : "Роль зафиксирована на время матча",
            width / 2, panel.y() + 36, Ui.MUTED);

        super.extractRenderState(g, mx, my, delta);

        for (int i = 0; i < DraftPlusRole.values().length; i++) {
            DraftPlusRole role = DraftPlusRole.values()[i];
            Ui.R rect = roleRects[i];
            boolean selected = role.id().equals(selectedId);
            Ui.button(g, rect, rect.in(mx, my), canChoose, selected ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
            Gfx.text(g, role.title(), rect.x() + 10, rect.y() + 7, selected ? Ui.GREEN : Ui.TEXT);
            Gfx.text(g, Ui.trunc(role.bonus(), rect.w() - 18), rect.x() + 10, rect.y() + 23, Ui.MUTED);
            roleButtons[i].active = canChoose;
        }

        Ui.button(g, backRect, backRect.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, "Назад", backRect.x() + backRect.w() / 2, backRect.cy() - 4, Ui.TEXT);
    }
}
