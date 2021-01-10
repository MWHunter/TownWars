package org.abyssmc.townwars;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

// Class heavily inspired by LandClaims
// https://github.com/pl3xgaming/LandClaims/blob/master/src/main/java/net/pl3x/bukkit/claims/configuration/Lang.java
public class LocaleReader {
    public static String PREFIX;

    public static String ASSIST_NATION_ATTACK;
    public static String ASSIST_NATION_DEFENSE;
    //public static String ATTACKERS_SCORED_KILL;
    public static String ATTACKERS_NOW_AT_PEACE_NATION_WAR;
    public static String ATTACKERS_NOW_AT_PEACE_TOWN_WAR;
    public static String ATTACKERS_YOU_TIED_NATION_WAR;
    public static String ATTACKERS_YOU_TIED_TOWN_WAR;
    public static String ATTACKERS_YOU_LOST_NATION_WAR;
    public static String ATTACKERS_YOU_LOST_TOWN_WAR;
    public static String ATTACKERS_YOU_WON_NATION_WAR;
    public static String ATTACKERS_YOU_WON_TOWN_WAR;
    public static String ATTACKING_NATIONS_YOU_LOST_NATION_WAR;
    public static String ATTACKING_NATIONS_YOU_WON_NATION_WAR;
    public static String COMMAND_AN_ERROR_OCCURRED;
    public static String COMMAND_ALLY_OF_BOTH_ATTACKERS_AND_DEFENDERS;
    public static String COMMAND_CANNOT_ATTACK_ALLIES;
    public static String COMMAND_CANNOT_ATTACK_ALREADY_AT_WAR;
    public static String COMMAND_CANNOT_ATTACK_AS_NEUTRAL;
    public static String COMMAND_CANNOT_ATTACK_NEUTRAL;
    public static String COMMAND_CANNOT_ATTACK_NOT_ENOUGH_DEFENDERS_ONLINE;
    public static String COMMAND_CANNOT_ATTACK_YOURSELF;
    public static String COMMAND_HELP;
    public static String COMMAND_MUST_BE_ENEMIES;
    public static String COMMAND_MUST_SPECIFY_TOWN_BECAUSE_MULTIPLE_WARS;
    public static String COMMAND_MUST_SPECIFY_WAR_TO_END;
    public static String COMMAND_MUST_SPECIFY_WAR_TO_SURRENDER;
    public static String COMMAND_NEXT_PEACE_REQUEST_TIME;
    public static String COMMAND_NOT_ALLY_OF_DEFENDER_OR_ATTACKER;
    public static String COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR;
    public static String COMMAND_NOT_ENOUGH_BALANCE_TOWN_WAR;
    public static String COMMAND_NOT_IN_ANY_WARS;
    public static String COMMAND_NOT_NATION_WAR;
    public static String COMMAND_NOT_PART_OF_NATION;
    public static String COMMAND_NOT_PART_OF_TOWN;
    public static String COMMAND_NOW_AT_WAR_WITH_NATION;
    public static String COMMAND_NOW_AT_WAR_WITH_TOWN;
    public static String COMMAND_NO_SPECIFIED_TOWN;
    public static String COMMAND_NO_SPECIFIED_WAR;
    public static String COMMAND_NOTHING_TO_CONFIRM;
    public static String COMMAND_ONLY_CAPITAL_CAN_ATTACK_CAPITAL;
    public static String COMMAND_PEACE_REQUEST_SENT;
    public static String COMMAND_TOWN_NOT_FOUND;
    public static String CONFIRMATION_EXPIRED;
    public static String CONFIRM_START_NATION_WAR;
    public static String CONFIRM_START_TOWN_WAR;
    public static String CONFIRM_JOIN_NATION_WAR_ATTACKERS;
    public static String CONFIRM_JOIN_NATION_WAR_DEFENDERS;
    public static String DEFENDERS_NATION_WAR_WAS_DECLARED;
    public static String DEFENDERS_TOWN_WAR_WAS_DECLARED;
    //public static String DEFENDERS_SCORED_KILL;
    public static String DEFENDERS_NOW_AT_PEACE_NATION_WAR;
    public static String DEFENDERS_NOW_AT_PEACE_TOWN_WAR;
    public static String DEFENDERS_YOU_TIED_NATION_WAR;
    public static String DEFENDERS_YOU_TIED_TOWN_WAR;
    public static String DEFENDERS_YOU_LOST_NATION_WAR;
    public static String DEFENDERS_YOU_LOST_TOWN_WAR;
    public static String DEFENDERS_YOU_WON_NATION_WAR;
    public static String DEFENDERS_YOU_WON_TOWN_WAR;
    public static String DEFENDING_NATIONS_YOU_LOST_NATION_WAR;
    public static String DEFENDING_NATIONS_YOU_WON_NATION_WAR;
    public static String DEPOSIT_ERROR;
    public static String NATION_WAR_DEPOSIT;
    public static String NATION_WAR_TIME_LEFT;
    public static String NATION_WAR_JOIN_ATTACKERS;
    public static String NATION_WAR_JOIN_DEFENDERS;
    public static String NATION_WAR_WITHDRAW;
    public static String NOT_OCCUPYING_WARNING;
    public static String NO_PERMISSION;
    public static String ATTACKING_NATION_WANTS_PEACE;
    public static String DEFENDING_NATION_WANTS_PEACE;
    public static String ATTACKING_TOWN_WARTS_PEACE;
    public static String DEFENDING_TOWN_WANTS_PEACE;
    public static String TOWN_CAPTURE_NOW_PAUSED_IN_COMBAT;
    public static String TOWN_CAPTURE_RESUMED_NO_LONGER_IN_COMBAT;
    public static String TOWN_WANTS_PEACE;
    public static String TOWN_WAR_DEPOSIT;
    public static String TOWN_WAR_TIME_LEFT;
    public static String TOWN_WAR_JOIN;
    public static String TOWN_WAR_WITHDRAW;
    public static String WAR_JOINED_AS_ATTACKER;
    public static String WAR_JOINED_AS_DEFENDER;
    public static String WAR_LIST_FORMAT_NATION;
    public static String WAR_LIST_FORMAT_TOWN;
    public static String WAR_LIST_HEADER;
    public static String WITHDRAW_ERROR;

    public static void reload() {
        String langFile = "lang-en.txt";
        File configFile = new File(TownWars.plugin.getDataFolder(), langFile);
        if (!configFile.exists()) {
            TownWars.plugin.saveResource(langFile, false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        PREFIX = colorize(config.getString("prefix", "&f[&aTownWars&f] "));

        ASSIST_NATION_ATTACK = config.getString("assist-nation-attack", "&bAssist &f{NATION_ATTACKER}&b &bconquer &f{NATION_DEFENDER}&b &bby running /war join {CAPITAL_BEING_SIEGED}&b");
        ASSIST_NATION_DEFENSE = config.getString("assist-nation-defense", "&bAssist &f{NATION_DEFENDER}&b defend against &f{NATION_ATTACKER}&b &bby running /war join {CAPITAL_BEING_SIEGED}&b");
        //ATTACKERS_SCORED_KILL = config.getString("attackers-scored-kill", "&bAttackers &f{ATTACKERS_KILLS}&b &a| &bDefenders &f{DEFENDERS_KILLS}&b");
        ATTACKERS_NOW_AT_PEACE_NATION_WAR = config.getString("attackers-now-at-peace-nation-war", "&bYou are now at peace with &f{NATION_DEFENDERS}");
        ATTACKERS_NOW_AT_PEACE_TOWN_WAR = config.getString("attackers-now-at-peace-town-war", "&bYou are now at peace with &f{DEFENDERS}");
        ATTACKERS_YOU_TIED_NATION_WAR = config.getString("attackers-you-tied-nation-war", "&bYou drew the war against &f{NATION_DEFENDERS}&b");
        ATTACKERS_YOU_TIED_TOWN_WAR = config.getString("attackers-you-tied-town-war", "&bYou drew the war against &f{DEFENDERS}&b");
        ATTACKERS_YOU_LOST_NATION_WAR = config.getString("attackers-you-lost-nation-war", "&bYou failed to siege &f{NATION_DEFENDERS}&b");
        ATTACKERS_YOU_LOST_TOWN_WAR = config.getString("attackers-you-lost-town-war", "&bYou failed to siege &f{DEFENDERS}&b");
        ATTACKERS_YOU_WON_NATION_WAR = config.getString("attackers-you-won-nation-war", "&bYou won &f{CLAIMS} claims&b for winning the nation war against &f{NATION_DEFENDER}&b");
        ATTACKERS_YOU_WON_TOWN_WAR = config.getString("attackers-you-won-town-war", "&bYou won &f{CLAIMS} claims&b for winning the war against &f{DEFENDERS}&b");
        ATTACKING_NATIONS_YOU_LOST_NATION_WAR = config.getString("attacking-nations-you-lost-nation-war", "&bYou lost the war helping &f{ATTACKERS}&b siege &f{DEFENDERS}&b");
        ATTACKING_NATIONS_YOU_WON_NATION_WAR = config.getString("attacking-nations-you-won-nation-war", "&bYou won &f{CLAIMS} claims&b for helping &f{NATION_ATTACKER}&b win the nation war against &f{NATION_DEFENDER}&b");
        COMMAND_AN_ERROR_OCCURRED = config.getString("command-an-error-occurred", "&4An unknown error has occurred.");
        COMMAND_ALLY_OF_BOTH_ATTACKERS_AND_DEFENDERS = config.getString("command-ally-of-both-attackers-and-defenders", "&cYou cannot join this war because you are allied to both &f{NATION_ATTACKER}&c and &f{NATION_DEFENDER}");
        COMMAND_CANNOT_ATTACK_ALLIES = config.getString("command-cannot-attack-allies", "&cYou cannot attack your allies");
        COMMAND_CANNOT_ATTACK_ALREADY_AT_WAR = config.getString("command-cannot-attack-already-at-war", "&cYou are already at war!");
        COMMAND_CANNOT_ATTACK_AS_NEUTRAL = config.getString("command-cannot-attack-as-neutral", "&cA neutral nation cannot go to war");
        COMMAND_CANNOT_ATTACK_NEUTRAL = config.getString("command-cannot-attack-neutral", "&cA neutral nation cannot be attacked");
        COMMAND_CANNOT_ATTACK_NOT_ENOUGH_DEFENDERS_ONLINE = config.getString("command-cannot-attack-not-enough-defenders-online", "&bDefenders must have both &f{PERCENT}%&b and &f{NUMBER_WITH_PERCENT}&b players online or at least &f{NUMBER}&b players online to attack");
        COMMAND_CANNOT_ATTACK_YOURSELF = config.getString("command-cannot-attack-yourself", "&cYou cannot attack yourself!");
        COMMAND_HELP = config.getString("command-help", "&9War command help\n/war list - see current wars\n/war declare [town] - start a war\n/war end [town] - request to end a war peacefully\n/war surrender [town] - avoid combat and forfeit the war");
        COMMAND_MUST_BE_ENEMIES = config.getString("command-must-be-enemies", "&cYou must be a enemy of the defending nation");
        COMMAND_MUST_SPECIFY_TOWN_BECAUSE_MULTIPLE_WARS = config.getString("command-must-specify-towns-because-multiple-wars", "You are in multiple wars, specify a town");
        COMMAND_MUST_SPECIFY_WAR_TO_END = config.getString("command-must-specify-war-to-end", "&cYou must specify a town");
        COMMAND_MUST_SPECIFY_WAR_TO_SURRENDER = config.getString("command-must-specify-war-to-surrender", "&cYou must specify a town");
        COMMAND_NEXT_PEACE_REQUEST_TIME = config.getString("command-next-peace-request-time", "&bYou can send the next peace request in &f{TIME_LEFT}");
        COMMAND_NOT_ALLY_OF_DEFENDER_OR_ATTACKER = config.getString("command-not-ally-of-defender-or-attacker", "&cYou must be a nation ally of defender or attacker to join a war");
        COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR = config.getString("command-not-enough-balance-nation-war", "&cYou need &a$&f{COST}&c to start a nation war");
        COMMAND_NOT_ENOUGH_BALANCE_TOWN_WAR = config.getString("command-not-enough-balance-town-war", "&cYou need &a$&f{COST}&c to start a town war");
        COMMAND_NOT_IN_ANY_WARS = config.getString("command-not-in-any-wars", "&cYou are not in any wars");
        COMMAND_NOT_NATION_WAR = config.getString("command-not-nation-war", "&cYou cannot join a town war");
        COMMAND_NOT_PART_OF_NATION = config.getString("command-not-part-of-nation", "&cYou must be in a nation");
        COMMAND_NOT_PART_OF_TOWN = config.getString("command-not-part-of-town", "&cYou must be in a town");
        COMMAND_NOW_AT_WAR_WITH_NATION = config.getString("command-now-at-war-with-nation", "&bYou are now at war with &f{NATION_DEFENDER}&b");
        COMMAND_NOW_AT_WAR_WITH_TOWN = config.getString("command-now-at-war-with-town", "&bYou are now at war with &f{DEFENDERS}&b");
        COMMAND_NO_SPECIFIED_TOWN = config.getString("command-no-specified-town", "&CYou must specify a town");
        COMMAND_NO_SPECIFIED_WAR = config.getString("command-no-specified-war", "&cUnable to locate war");
        COMMAND_NOTHING_TO_CONFIRM = config.getString("command-nothing-to-confirm", "&cThere is nothing to confirm");
        COMMAND_ONLY_CAPITAL_CAN_ATTACK_CAPITAL = config.getString("command-only-capital-can-attack-capital", "&cOnly a capital town can attack a capital town");
        COMMAND_PEACE_REQUEST_SENT = config.getString("command-peace-request-sent", "&bA peace request has been sent");
        COMMAND_TOWN_NOT_FOUND = config.getString("command-town-not-specified", "&cUnable to locate town");
        CONFIRMATION_EXPIRED = config.getString("confirmation-expired", "&cYour action has expired - you only have &f{SECONDS_TO_CONFIRM} seconds&c to confirm war");
        CONFIRM_START_NATION_WAR = config.getString("confirm-start-nation-war", "&fYou are declaring a nation war\n&bNations Attacking: {NATION_ATTACKERS}\n&bNation Defending: {NATION_DEFENDERS}\n&bTime Limit: {TIME_LIMIT}\n&bKills to Win: {KILLS_TO_WIN}\n&b&bCost to start: {COST}\n&cConfirm with /war confirm");
        CONFIRM_START_TOWN_WAR = config.getString("confirm-start-town-war", "&fYou are declaring a town war\n&bAttackers: {ATTACKERS}\n&bDefenders: {DEFENDERS}\n&bTime Limit: {TIME_LIMIT}\n&bCost to start: {COST}\n&cConfirm with /war confirm");
        CONFIRM_JOIN_NATION_WAR_ATTACKERS = config.getString("confirm-join-nation-war-attackers", "&9You are joining a nation war as an attacker\n&bNations Attacking: {NATION_ATTACKERS}\n&bNations Defending: {NATION_DEFENDERS}\n&bTime Left: {TIME_LEFT}\n&bCost to join: {COST}\n&cConfirm with /war confirm");
        CONFIRM_JOIN_NATION_WAR_DEFENDERS = config.getString("confirm-join-nation-war-defenders", "&9You are joining a nation war as a defender\n&bNations Attacking: {NATION_ATTACKERS}\n&bNations Defending: {NATION_DEFENDERS}\n&bTime Left: {TIME_LEFT}\n&bCost to join: {COST}\n&cConfirm with /war confirm");
        DEFENDERS_NATION_WAR_WAS_DECLARED = config.getString("defenders-nation-war-was-declared", "&f{NATION_ATTACKERS}&b has declared war on &f{NATION_DEFENDERS}&b!");
        DEFENDERS_TOWN_WAR_WAS_DECLARED = config.getString("defenders-town-war-was-declared", "&f{ATTACKERS}&b has declared war on &f{DEFENDERS}&b");
        //DEFENDERS_SCORED_KILL = config.getString("defenders-scored-kill", "&bAttackers &f{ATTACKERS_KILLS}&b &a| &bDefenders &f{DEFENDERS_KILLS}&b");
        DEFENDERS_NOW_AT_PEACE_NATION_WAR = config.getString("defenders-now-at-peace-nation-war", "&bYou are now at peace with &f{NATION_ATTACKERS}");
        DEFENDERS_NOW_AT_PEACE_TOWN_WAR = config.getString("defenders-now-at-peace-town-war", "&bYou are now at peace with &f{ATTACKERS}");
        DEFENDERS_YOU_TIED_NATION_WAR = config.getString("defenders-you-lost-nation-war", "&bYou drew the war against &f{NATION_ATTACKERS}&b");
        DEFENDERS_YOU_TIED_TOWN_WAR = config.getString("defenders-you-lost-town-war", "&bYou drew the war against &f{ATTACKERS}&b");
        DEFENDERS_YOU_LOST_NATION_WAR = config.getString("defenders-you-lost-nation-war", "&bYou lost &f{CLAIMS} claims&b for losing the nation war against &f{NATION_ATTACKERS}&b");
        DEFENDERS_YOU_LOST_TOWN_WAR = config.getString("defenders-you-lost-town-war", "&bYou lost &f{CLAIMS} claims&b for losing the war against &f{ATTACKERS}&b");
        DEFENDERS_YOU_WON_NATION_WAR = config.getString("defenders-you-won-nation-war", "&bYou defended your town against &f{NATION_ATTACKERS}&b and have received &a$&f{COST}&b&b");
        DEFENDERS_YOU_WON_TOWN_WAR = config.getString("defenders-you-won-town-war", "&bYou defended your town against &f{ATTACKERS}&b and have received &a$&f{COST}&b&b");
        DEFENDING_NATIONS_YOU_LOST_NATION_WAR = config.getString("defending-nations-you-lost-nation-war", "&bYou lost the war helping &f{NATION_DEFENDERS}&b defend their nation from &f{NATION_ATTACKERS}&b");
        DEFENDING_NATIONS_YOU_WON_NATION_WAR = config.getString("defending-nations-you-won-nation-war", "&bYou won the war helping &f{NATION_DEFENDERS}&b defend their nation from &f{NATION_ATTACKERS}&b and have received &a$&f{COST}&b&b");
        DEPOSIT_ERROR = config.getString("deposit-error", "An error occurred while depositing funds");
        NATION_WAR_DEPOSIT = config.getString("nation-war-deposit", "Winning nation war");
        NATION_WAR_TIME_LEFT = config.getString("nation-war-time-left", "&bThe nation war will end in &f{TIME_LEFT}");
        NATION_WAR_JOIN_ATTACKERS = config.getString("nation-war-join-attackers", "Joining nation war as attackers");
        NATION_WAR_JOIN_DEFENDERS = config.getString("nation-war-join-defenders", "Joining nation war as defenders");
        NATION_WAR_WITHDRAW = config.getString("nation-war-withdraw", "Starting nation war");
        NO_PERMISSION = config.getString("no-permission", "&cYou do not have permission");
        NOT_OCCUPYING_WARNING = config.getString("not-occupying-warning", "&cYou will lose the war in &f{SECONDS_NOT_OCCUPIED_UNTIL_FAIL} &cif no attacking players are in &f{DEFENDERS}'s&c land");
        TOWN_CAPTURE_NOW_PAUSED_IN_COMBAT = config.getString("town-capture-now-paused-in-combat", "&9{DEFENDERS} capture &fpaused");
        TOWN_CAPTURE_RESUMED_NO_LONGER_IN_COMBAT = config.getString("town-capture-resumed-no-longer-in-combat", "&9{DEFENDERS} capture &fresumed");
        TOWN_WANTS_PEACE = config.getString("town-wants-peace", "&a&f{TOWN}&b wants peace");
        TOWN_WAR_DEPOSIT = config.getString("town-war-deposit", "Winning town war");
        TOWN_WAR_TIME_LEFT = config.getString("town-war-time-left", "&bThe war will end in &f{TIME_LEFT}");
        TOWN_WAR_JOIN = config.getString("town-war-join", "Joining town war");
        TOWN_WAR_WITHDRAW = config.getString("town-war-withdraw", "Starting town war");
        WAR_JOINED_AS_ATTACKER = config.getString("war-joined-as-attacker", "&f{NATION}&b &bhas joined the war to help &f{NATION_DEFENDERS}&b &bdefend against &f{NATION_ATTACKERS}");
        WAR_JOINED_AS_DEFENDER = config.getString("war-joined-as-defender", "&f{NATION}&b &bhas joined the war to help &f{NATION_ATTACKERS}&b &bdefeat &f{NATION_DEFENDERS}&b");
        WAR_LIST_FORMAT_NATION = config.getString("war-list-format-nation", "&bAttackers: &f{NATION_ATTACKERS}&b &bDefenders: &f{NATION_DEFENDERS} &bTime left: &f{TIME_LEFT}");
        WAR_LIST_FORMAT_TOWN = config.getString("war-list-format-town", "&bAttackers: &f{ATTACKERS}&b &bDefenders: &f{DEFENDERS} &bTime left: &f{TIME_LEFT}");
        WAR_LIST_HEADER = config.getString("war-list-header", "&bWar list");
        WITHDRAW_ERROR = config.getString("withdraw-error", "&cAn error occurred while withdrawing funds");

        ATTACKING_NATION_WANTS_PEACE = config.getString("attacking-nation-wants-peace", "&b{NATION_ATTACKER} want peace");
        DEFENDING_NATION_WANTS_PEACE = config.getString("defending-nation-wants-peace", "&b{NATION_DEFENDER} want peace");
        ATTACKING_TOWN_WARTS_PEACE = config.getString("attacking-town-wants-peace", "&b{ATTACKERS} want peace");
        DEFENDING_TOWN_WANTS_PEACE = config.getString("defending-town-wants-peace", "&b{DEFENDERS} want peace");
    }

    public static void send(CommandSender recipient, String message) {
        if (recipient != null) {
            for (String part : colorize(message).split("\n")) {
                if (part != null && !part.isEmpty()) {
                    recipient.sendMessage(PREFIX + part);
                }
            }
        }
    }

    public static String colorize(String str) {
        if (str == null) {
            return "";
        }
        str = ChatColor.translateAlternateColorCodes('&', str);
        if (ChatColor.stripColor(str).isEmpty()) {
            return "";
        }
        return str;
    }
}
