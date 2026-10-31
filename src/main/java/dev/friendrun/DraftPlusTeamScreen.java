package dev.friendrun;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Team selection and roster management for Draft+ Team format. */
public final class DraftPlusTeamScreen extends Screen {
    private final Screen parent;
    private Button joinAButton, joinBButton, leaveButton, randomButton, renameButton, backButton;
    private EditBox teamNameBox;
    private Ui.R panel, joinA, joinB, leave, random, rename, back, nameBox;
    private int px, py, pw, ph;

    public DraftPlusTeamScreen(Screen parent) {
        super(Component.literal("FriendRun — Draft+ команды"));
        this.parent = parent;
    }

    private Button make(Ui.R r, Runnable action) {
        Button b = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
            .bounds(r.x(), r.y(), r.w(), r.h()).build());
        Ui.invisible(b);
        return b;
    }

    private static List<DraftPlusTeams.Member> roster(RunState state) {
        List<DraftPlusTeams.Member> members = new ArrayList<>();
        for (RunState.PlayerInfo p : state.players)
            members.add(new DraftPlusTeams.Member(p.name(), p.joinedAt(), p.draftPlusTeam()));
        return members;
    }

    private int myTeam(RunState state) {
        String me = FriendRunClient.accountName();
        for (RunState.PlayerInfo p : state.players)
            if (p.name().equals(me)) return p.draftPlusTeam();
        return DraftPlusTeams.NO_TEAM;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 20, 470);
        ph = Math.min(height - 16, 280);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        panel = new Ui.R(px, py, pw, ph);

        int inner = pw - 24;
        int half = (inner - 8) / 2;
        joinA = new Ui.R(px + 12, py + 64, half, 24);
        joinB = new Ui.R(px + 12 + half + 8, py + 64, half, 24);
        leave = new Ui.R(px + 12, py + 94, inner, 22);
        random = new Ui.R(px + 12, py + 122, inner, 22);
        nameBox = new Ui.R(px + 12, py + 174, inner - 82, 22);
        rename = new Ui.R(nameBox.x() + nameBox.w() + 6, nameBox.y(), 76, 22);
        back = new Ui.R(px + 12, py + ph - 34, inner, 22);

        joinAButton = make(joinA, () -> FriendRunClient.setDraftPlusTeam(DraftPlusTeams.TEAM_A));
        joinBButton = make(joinB, () -> FriendRunClient.setDraftPlusTeam(DraftPlusTeams.TEAM_B));
        leaveButton = make(leave, () -> FriendRunClient.setDraftPlusTeam(DraftPlusTeams.NO_TEAM));
        randomButton = make(random, FriendRunClient::randomizeDraftPlusTeams);
        renameButton = make(rename, () -> {
            int team = myTeam(FriendRunClient.STATE);
            if (team == DraftPlusTeams.TEAM_A || team == DraftPlusTeams.TEAM_B)
                FriendRunClient.renameDraftPlusTeam(team, teamNameBox.getValue());
        });
        backButton = make(back, () -> Mc.setScreen(parent));

        teamNameBox = new EditBox(font, nameBox.x() + 7, nameBox.y() + 6, nameBox.w() - 14, 12,
            Component.literal("Название команды"));
        teamNameBox.setMaxLength(DraftPlusTeams.MAX_TEAM_NAME_LENGTH);
        Ui.noBorder(teamNameBox);
        addRenderableWidget(teamNameBox);
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        RunState s = FriendRunClient.STATE;
        List<DraftPlusTeams.Member> members = roster(s);
        int countA = (int) members.stream().filter(m -> m.teamIndex() == DraftPlusTeams.TEAM_A).count();
        int countB = (int) members.stream().filter(m -> m.teamIndex() == DraftPlusTeams.TEAM_B).count();
        int mine = myTeam(s);
        int captainTeam = mine == DraftPlusTeams.TEAM_A || mine == DraftPlusTeams.TEAM_B
            ? (DraftPlusTeams.isCaptain(members, FriendRunClient.accountName(), mine) ? mine : DraftPlusTeams.NO_TEAM)
            : DraftPlusTeams.NO_TEAM;
        boolean editable = !s.launched && s.phase == RunState.Phase.IDLE;
        boolean canJoinA = editable && countA < s.draftPlusTeamSize && mine != DraftPlusTeams.TEAM_A;
        boolean canJoinB = editable && countB < s.draftPlusTeamSize && mine != DraftPlusTeams.TEAM_B;
        boolean canRename = editable && captainTeam != DraftPlusTeams.NO_TEAM;
        boolean canRandomize = editable && s.amHost && s.players.size() <= s.draftPlusTeamSize * 2;

        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.CROWN, px + 14, py + 12, 2, Ui.GOLD);
        Ui.bold(g, "Draft+ · Команды", px + 36, py + 12, Ui.GOLD);
        Gfx.text(g, "Составы и капитаны определяются по времени входа в лобби", px + 14, py + 34, Ui.MUTED);

        int colW = (pw - 36) / 2;
        int colY = py + 48;
        Ui.frame(g, px + 12, colY, colW, 14, Ui.SECTION, Ui.LINE);
        Ui.frame(g, px + 24 + colW, colY, colW, 14, Ui.SECTION, Ui.LINE);
        Gfx.text(g, Ui.trunc(s.draftPlusTeamNameA + " · " + countA + "/" + s.draftPlusTeamSize, colW - 10), px + 17, colY + 3, Ui.TEXT);
        Gfx.text(g, Ui.trunc(s.draftPlusTeamNameB + " · " + countB + "/" + s.draftPlusTeamSize, colW - 10), px + 29 + colW, colY + 3, Ui.TEXT);

        super.extractRenderState(g, mx, my, delta);

        Ui.button(g, joinA, joinA.in(mx, my), canJoinA, mine == DraftPlusTeams.TEAM_A ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, mine == DraftPlusTeams.TEAM_A ? "Вы в команде A" : "Вступить в A",
            joinA.x() + joinA.w() / 2, joinA.cy() - 4, canJoinA || mine == DraftPlusTeams.TEAM_A ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, joinB, joinB.in(mx, my), canJoinB, mine == DraftPlusTeams.TEAM_B ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, mine == DraftPlusTeams.TEAM_B ? "Вы в команде B" : "Вступить в B",
            joinB.x() + joinB.w() / 2, joinB.cy() - 4, canJoinB || mine == DraftPlusTeams.TEAM_B ? Ui.TEXT : Ui.MUTED);

        Ui.button(g, leave, leave.in(mx, my), editable && mine != DraftPlusTeams.NO_TEAM, Ui.Kind.NORMAL);
        Gfx.centered(g, "Покинуть текущую команду", leave.x() + leave.w() / 2, leave.cy() - 4,
            editable && mine != DraftPlusTeams.NO_TEAM ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, random, random.in(mx, my), canRandomize, Ui.Kind.NORMAL);
        Gfx.centered(g, s.amHost ? "Случайно распределить игроков" : "Случайное распределение — только хост",
            random.x() + random.w() / 2, random.cy() - 4, canRandomize ? Ui.TEXT : Ui.MUTED);

        String ownTeamName = mine == DraftPlusTeams.TEAM_A ? s.draftPlusTeamNameA
            : mine == DraftPlusTeams.TEAM_B ? s.draftPlusTeamNameB : "";
        if (teamNameBox.getValue().isBlank() && !ownTeamName.isBlank()) teamNameBox.setValue(ownTeamName);
        Ui.frame(g, nameBox.x(), nameBox.y(), nameBox.w(), nameBox.h(), Ui.SECTION, Ui.LINE);
        Ui.button(g, rename, rename.in(mx, my), canRename, Ui.Kind.NORMAL);
        Gfx.centered(g, "Переименовать", rename.x() + rename.w() / 2, rename.cy() - 4, canRename ? Ui.TEXT : Ui.MUTED);
        Gfx.text(g, canRename ? "Имя команды (только капитан)" : "Имя команды (доступно капитану)",
            nameBox.x(), nameBox.y() - 12, Ui.MUTED);

        int listY = py + 204;
        Gfx.text(g, "Игроки:", px + 14, listY, Ui.MUTED);
        int row = 0;
        for (RunState.PlayerInfo p : s.players) {
            String team = p.draftPlusTeam() == DraftPlusTeams.TEAM_A ? "A"
                : p.draftPlusTeam() == DraftPlusTeams.TEAM_B ? "B" : "—";
            boolean captain = p.draftPlusTeam() >= 0
                && DraftPlusTeams.isCaptain(members, p.name(), p.draftPlusTeam());
            int x = px + 14 + (row / 3) * ((pw - 28) / 2);
            int y = listY + 14 + (row % 3) * 12;
            String label = p.name() + (captain ? " ★" : "") + "  [" + team + "]";
            Gfx.text(g, Ui.trunc(label, (pw - 36) / 2), x, y, p.name().equals(FriendRunClient.accountName()) ? Ui.GOLD : Ui.TEXT);
            row++;
            if (row >= 10) break;
        }

        Ui.button(g, back, back.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, "Назад", back.x() + back.w() / 2, back.cy() - 4, Ui.TEXT);

        joinAButton.active = canJoinA;
        joinBButton.active = canJoinB;
        leaveButton.active = editable && mine != DraftPlusTeams.NO_TEAM;
        randomButton.active = canRandomize;
        renameButton.active = canRename;
        backButton.active = true;
    }
}
