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
        super(Component.literal(Tr.t("friendrun.draft_plus.team.title", "Draft+ · Команды")));
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
        String me = FriendRunClient.myName();
        for (RunState.PlayerInfo p : state.players)
            if (p.name().equals(me)) return p.draftPlusTeam();
        return DraftPlusTeams.NO_TEAM;
    }

    private void drawRosterSlot(GuiGraphicsExtractor g, RunState.PlayerInfo player,
                                List<DraftPlusTeams.Member> members, int team,
                                int x, int y, int width) {
        Ui.R slot = new Ui.R(x, y - 2, width, 14);
        Ui.frame(g, slot.x(), slot.y(), slot.w(), slot.h(), Ui.SECTION, Ui.LINE);
        if (player == null) {
            Gfx.text(g, Tr.t("friendrun.draft_plus.team.empty_slot", "— свободное место —"),
                x + 4, y + 1, Ui.MUTED);
            return;
        }
        boolean captain = DraftPlusTeams.isCaptain(members, player.name(), team);
        String label = player.name() + (captain ? " ★" : "");
        int color = player.name().equals(FriendRunClient.myName()) ? Ui.GOLD : Ui.TEXT;
        Gfx.text(g, Ui.trunc(label, width - 8), x + 4, y + 1, color);
    }

    @Override
    protected void init() {
        pw = Math.min(width - 20, 470);
        ph = Math.min(height - 16, 360);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        panel = new Ui.R(px, py, pw, ph);

        int inner = pw - 24;
        int half = (inner - 8) / 2;
        joinA = new Ui.R(px + 12, py + 166, half, 24);
        joinB = new Ui.R(px + 12 + half + 8, py + 166, half, 24);
        leave = new Ui.R(px + 12, py + 196, inner, 22);
        random = new Ui.R(px + 12, py + 224, inner, 22);
        nameBox = new Ui.R(px + 12, py + 272, inner - 82, 22);
        rename = new Ui.R(nameBox.x() + nameBox.w() + 6, nameBox.y(), 76, 22);
        back = new Ui.R(px + 12, py + ph - 28, inner, 22);

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
            Component.literal(Tr.t("friendrun.draft_plus.team.name_hint", "Название команды")));
        teamNameBox.setMaxLength(DraftPlusTeams.MAX_TEAM_NAME_LENGTH);
        int initialTeam = myTeam(FriendRunClient.STATE);
        if (initialTeam == DraftPlusTeams.TEAM_A) teamNameBox.setValue(FriendRunClient.STATE.draftPlusTeamNameA);
        else if (initialTeam == DraftPlusTeams.TEAM_B) teamNameBox.setValue(FriendRunClient.STATE.draftPlusTeamNameB);
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
            ? (DraftPlusTeams.isCaptain(members, FriendRunClient.myName(), mine) ? mine : DraftPlusTeams.NO_TEAM)
            : DraftPlusTeams.NO_TEAM;
        boolean editable = !s.launched && s.phase == RunState.Phase.IDLE;
        boolean canJoinA = editable && countA < s.draftPlusTeamSize && mine != DraftPlusTeams.TEAM_A;
        boolean canJoinB = editable && countB < s.draftPlusTeamSize && mine != DraftPlusTeams.TEAM_B;
        boolean canRename = editable && captainTeam != DraftPlusTeams.NO_TEAM;
        boolean canRandomize = editable && s.amHost && s.players.size() <= s.draftPlusTeamSize * 2;

        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.CROWN, px + 14, py + 12, 2, Ui.GOLD);
        Ui.bold(g, Tr.t("friendrun.draft_plus.team.title", "Draft+ · Команды"), px + 36, py + 12, Ui.GOLD);
        Gfx.text(g, Tr.t("friendrun.draft_plus.team.roster_rule", "Составы и капитаны определяются по времени входа в лобби"), px + 14, py + 34, Ui.MUTED);

        super.extractRenderState(g, mx, my, delta);

        Ui.button(g, joinA, joinA.in(mx, my), canJoinA, mine == DraftPlusTeams.TEAM_A ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, mine == DraftPlusTeams.TEAM_A ? Tr.t("friendrun.draft_plus.team.you_a", "Вы в команде A")
                : Tr.t("friendrun.draft_plus.team.join_a", "Вступить в A"),
            joinA.x() + joinA.w() / 2, joinA.cy() - 4, canJoinA || mine == DraftPlusTeams.TEAM_A ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, joinB, joinB.in(mx, my), canJoinB, mine == DraftPlusTeams.TEAM_B ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, mine == DraftPlusTeams.TEAM_B ? Tr.t("friendrun.draft_plus.team.you_b", "Вы в команде B")
                : Tr.t("friendrun.draft_plus.team.join_b", "Вступить в B"),
            joinB.x() + joinB.w() / 2, joinB.cy() - 4, canJoinB || mine == DraftPlusTeams.TEAM_B ? Ui.TEXT : Ui.MUTED);

        Ui.button(g, leave, leave.in(mx, my), editable && mine != DraftPlusTeams.NO_TEAM, Ui.Kind.NORMAL);
        Gfx.centered(g, Tr.t("friendrun.draft_plus.team.leave", "Покинуть текущую команду"), leave.x() + leave.w() / 2, leave.cy() - 4,
            editable && mine != DraftPlusTeams.NO_TEAM ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, random, random.in(mx, my), canRandomize, Ui.Kind.NORMAL);
        Gfx.centered(g, s.amHost
            ? Tr.t("friendrun.draft_plus.team.randomize", "Случайно распределить игроков")
            : Tr.t("friendrun.draft_plus.team.randomize_host", "Случайное распределение — только хост"),
            random.x() + random.w() / 2, random.cy() - 4, canRandomize ? Ui.TEXT : Ui.MUTED);

        Ui.frame(g, nameBox.x(), nameBox.y(), nameBox.w(), nameBox.h(), Ui.SECTION, Ui.LINE);
        Ui.button(g, rename, rename.in(mx, my), canRename, Ui.Kind.NORMAL);
        Gfx.centered(g, Tr.t("friendrun.draft_plus.team.rename", "Переименовать"), rename.x() + rename.w() / 2, rename.cy() - 4, canRename ? Ui.TEXT : Ui.MUTED);
        Gfx.text(g, canRename
            ? Tr.t("friendrun.draft_plus.team.name_captain_only", "Имя команды (только капитан)")
            : Tr.t("friendrun.draft_plus.team.name_available_captain", "Имя команды (доступно капитану)"),
            nameBox.x(), nameBox.y() - 12, Ui.MUTED);

        // Два постоянных ростера: игроки занимают свои места сразу после вступления.
        int rosterY = py + 48;
        int colW = (pw - 36) / 2;
        int teamAX = px + 12;
        int teamBX = px + 24 + colW;
        int rosterH = 108;
        Ui.frame(g, teamAX, rosterY, colW, rosterH, Ui.SECTION, Ui.LINE);
        Ui.frame(g, teamBX, rosterY, colW, rosterH, Ui.SECTION, Ui.LINE);
        Gfx.text(g, Ui.trunc(s.draftPlusTeamNameA + " · " + countA + "/" + s.draftPlusTeamSize, colW - 10),
            teamAX + 6, rosterY + 5, Ui.GOLD);
        Gfx.text(g, Ui.trunc(s.draftPlusTeamNameB + " · " + countB + "/" + s.draftPlusTeamSize, colW - 10),
            teamBX + 6, rosterY + 5, Ui.GOLD);
        int slots = Math.max(1, s.draftPlusTeamSize);
        for (int i = 0; i < slots; i++) {
            int slotY = rosterY + 22 + i * 16;
            RunState.PlayerInfo playerA = members.stream()
                .filter(m -> m.teamIndex() == DraftPlusTeams.TEAM_A)
                .sorted(java.util.Comparator.comparingLong(DraftPlusTeams.Member::joinedAt))
                .skip(i).findFirst()
                .flatMap(m -> s.players.stream().filter(p -> p.name().equals(m.getName())).findFirst()).orElse(null); // Исправлено m.name() -> m.getName()
            RunState.PlayerInfo playerB = members.stream()
                .filter(m -> m.teamIndex() == DraftPlusTeams.TEAM_B)
                .sorted(java.util.Comparator.comparingLong(DraftPlusTeams.Member::joinedAt))
                .skip(i).findFirst()
                .flatMap(m -> s.players.stream().filter(p -> p.name().equals(m.getName())).findFirst()).orElse(null); // Исправлено m.name() -> m.getName()
            drawRosterSlot(g, playerA, members, DraftPlusTeams.TEAM_A, teamAX + 6, slotY, colW - 12);
            drawRosterSlot(g, playerB, members, DraftPlusTeams.TEAM_B, teamBX + 6, slotY, colW - 12);
        }

        Ui.button(g, back, back.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, Tr.t("friendrun.ui.back", "Назад"), back.x() + back.w() / 2, back.cy() - 4, Ui.TEXT);

        joinAButton.active = canJoinA;
        joinBButton.active = canJoinB;
        leaveButton.active = editable && mine != DraftPlusTeams.NO_TEAM;
        randomButton.active = canRandomize;
        renameButton.active = canRename;
        backButton.active = true;
    }
}
