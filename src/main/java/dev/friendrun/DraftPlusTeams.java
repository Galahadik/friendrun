package dev.friendrun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Pure team-lobby rules for Draft+. Team membership is independent from the
 * legacy Draft mode and from Minecraft/Firebase/UI state.
 */
public final class DraftPlusTeams {
    public static final int TEAM_A = 0;
    public static final int TEAM_B = 1;
    public static final int NO_TEAM = -1;
    public static final int MAX_TEAM_NAME_LENGTH = 20;

    /** joinedAt is the server-side lobby join timestamp in milliseconds. */
    public record Member(String id, long joinedAt, int teamIndex) {
        public Member {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Player id is blank");
            if (teamIndex < NO_TEAM || teamIndex > TEAM_B)
                throw new IllegalArgumentException("Invalid team index");
        }

        public Member inTeam(int team) {
            return new Member(id, joinedAt, team);
        }
    }

    private DraftPlusTeams() {}

    /** Returns members in stable join order; ties are resolved by player id. */
    public static List<Member> ordered(List<Member> members) {
        validateUnique(members);
        return members.stream()
            .sorted(Comparator.comparingLong(Member::joinedAt).thenComparing(Member::id))
            .toList();
    }

    /** The first player to join a team is its captain. No mutable captain flag is stored. */
    public static Optional<Member> captain(List<Member> members, int teamIndex) {
        checkTeam(teamIndex);
        return ordered(members).stream().filter(m -> m.teamIndex() == teamIndex).findFirst();
    }

    public static boolean isCaptain(List<Member> members, String playerId, int teamIndex) {
        return captain(members, teamIndex).map(m -> m.id().equals(playerId)).orElse(false);
    }

    public static List<Member> teamMembers(List<Member> members, int teamIndex) {
        checkTeam(teamIndex);
        return ordered(members).stream().filter(m -> m.teamIndex() == teamIndex).toList();
    }

    /** Join or switch teams. Passing NO_TEAM removes the player from their current team. */
    public static List<Member> setTeam(List<Member> members, String playerId, int teamIndex, int teamSize) {
        Objects.requireNonNull(playerId, "playerId");
        validateCapacity(teamSize);
        if (teamIndex != NO_TEAM) checkTeam(teamIndex);
        List<Member> current = ordered(members);
        Member target = current.stream().filter(m -> m.id().equals(playerId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Player is not in the lobby"));
        if (teamIndex != NO_TEAM && target.teamIndex() != teamIndex
            && current.stream().filter(m -> m.teamIndex() == teamIndex).count() >= teamSize)
            throw new IllegalArgumentException("Team is full");

        List<Member> updated = new ArrayList<>(current.size());
        for (Member member : current)
            updated.add(member.id().equals(playerId) ? member.inTeam(teamIndex) : member);
        return List.copyOf(updated);
    }

    /**
     * Randomly assigns all lobby members while keeping team sizes as balanced as possible.
     * The host supplies the Random instance; clients must not independently choose assignments.
     */
    public static List<Member> randomAssignment(List<Member> members, int teamSize, Random random) {
        Objects.requireNonNull(random, "random");
        validateCapacity(teamSize);
        List<Member> shuffled = new ArrayList<>(ordered(members));
        if (shuffled.size() > teamSize * 2)
            throw new IllegalArgumentException("There are more players than available team slots");
        java.util.Collections.shuffle(shuffled, random);
        int firstTeam = random.nextBoolean() ? TEAM_A : TEAM_B;
        List<Member> assigned = new ArrayList<>(shuffled.size());
        for (int i = 0; i < shuffled.size(); i++) {
            int team = (i % 2 == 0) ? firstTeam : 1 - firstTeam;
            assigned.add(shuffled.get(i).inTeam(team));
        }
        return List.copyOf(assigned);
    }

    /** Match can start only when both teams are exactly the configured size. */
    public static boolean isReady(List<Member> members, int teamSize) {
        validateCapacity(teamSize);
        try {
            validateUnique(members);
        } catch (IllegalArgumentException e) {
            return false;
        }
        long a = members.stream().filter(m -> m.teamIndex() == TEAM_A).count();
        long b = members.stream().filter(m -> m.teamIndex() == TEAM_B).count();
        long unassigned = members.stream().filter(m -> m.teamIndex() == NO_TEAM).count();
        return unassigned == 0 && a == teamSize && b == teamSize;
    }

    public static String validateTeamName(String name) {
        if (name == null) throw new IllegalArgumentException("Team name is missing");
        String clean = name.strip();
        if (clean.isEmpty()) throw new IllegalArgumentException("Team name cannot be empty");
        if (clean.length() > MAX_TEAM_NAME_LENGTH)
            throw new IllegalArgumentException("Team name is too long");
        if (clean.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Team name contains control characters");
        return clean;
    }

    private static void validateUnique(List<Member> members) {
        Objects.requireNonNull(members, "members");
        Set<String> ids = new HashSet<>();
        for (Member member : members) {
            Objects.requireNonNull(member, "member");
            if (!ids.add(member.id())) throw new IllegalArgumentException("Duplicate player id");
        }
    }

    private static void validateCapacity(int teamSize) {
        if (teamSize < 1 || teamSize > 5) throw new IllegalArgumentException("Team size must be 1 to 5");
    }

    private static void checkTeam(int teamIndex) {
        if (teamIndex != TEAM_A && teamIndex != TEAM_B)
            throw new IllegalArgumentException("Team index must be A or B");
    }
}
