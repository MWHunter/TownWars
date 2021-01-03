package org.abyssmc.townwars;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

// Class heavily inspired by LandClaims
// https://github.com/pl3xgaming/LandClaims/blob/master/src/main/java/net/pl3x/bukkit/claims/configuration/Lang.java
public class LocaleReader {
    public static String ASSIST_NATION_ATTACK;
    public static String ASSIST_NATION_DEFENSE;
    public static String ATTACKERS_YOU_LOST_NATION_WAR;
    public static String ATTACKERS_YOU_LOST_TOWN_WAR;
    public static String ATTACKERS_YOU_WON_NATION_WAR;
    public static String ATTACKERS_YOU_WON_TOWN_WAR;
    public static String COMMAND_AN_ERROR_OCCURRED;
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
    public static String COMMAND_ONLY_CAPITAL_CAN_ATTACK_CAPITAL;
    public static String COMMAND_PEACE_REQUEST_SENT;
    public static String COMMAND_TOWN_NOT_FOUND;
    public static String DEFENDERS_YOU_LOST_NATION_WAR;
    public static String DEFENDERS_YOU_LOST_TOWN_WAR;
    public static String DEFENDERS_YOU_WON_NATION_WAR;
    public static String DEFENDERS_YOU_WON_TOWN_WAR;
    public static String DEPOSIT_ERROR;
    public static String NATION_WAR_DEPOSIT;
    public static String NATION_WAR_JOIN;
    public static String NATION_WAR_WITHDRAW;
    public static String TOWN_WANTS_PEACE;
    public static String TOWN_WAR_DEPOSIT;
    public static String TOWN_WAR_JOIN;
    public static String TOWN_WAR_WITHDRAW;
    public static String WAR_JOINED_AS_ATTACKER;
    public static String WAR_JOINED_AS_DEFENDER;
    public static String WAR_LIST_FORMAT;
    public static String WAR_LIST_HEADER;
    public static String WITHDRAW_ERROR;

    public static void reload(JavaPlugin plugin) {
        String langFile = "lang-en.txt";
        File configFile = new File(plugin.getDataFolder(), langFile);
        if (!configFile.exists()) {
            plugin.saveResource(langFile, false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        ASSIST_NATION_ATTACK = config.getString("assist-nation-attack", "&bAssist &f{NATION_ATTACKERS} &bdefeat &f{NATION_DEFENDERS} &bby running /war join &f{CAPITAL_BEING_SIEGED}");
        ASSIST_NATION_DEFENSE = config.getString("assist-nation-defense", "&bAssist &f{NATION_DEFENDERS} &bdefeat &f{NATION_ATTACKERS} &bby running /war join &f{CAPITAL_BEING_SIEGED}");
        ATTACKERS_YOU_LOST_NATION_WAR = config.getString("attackers-you-lost-nation-war", "&bYou failed to siege {DEFENDERS}");
        ATTACKERS_YOU_LOST_TOWN_WAR = config.getString("attackers-you-lost-town-war", "&bYou failed to siege {DEFENDERS}");
        ATTACKERS_YOU_WON_NATION_WAR = config.getString("attackers-you-won-nation-war", "&bYou won {CLAIMS} for winning the nation war against {ATTACKERS}");
        ATTACKERS_YOU_WON_TOWN_WAR = config.getString("attackers-you-won-town-war", "&bYou won {CLAIMS} for winning the war against {ATTACKERS}");
        COMMAND_AN_ERROR_OCCURRED = config.getString("command-an-error-occurred", "&4An unknown error has occurred.");
        COMMAND_CANNOT_ATTACK_ALLIES = config.getString("command-cannot-attack-allies", "&cYou cannot attack your allies");
        COMMAND_CANNOT_ATTACK_ALREADY_AT_WAR = config.getString("command-cannot-attack-already-at-war", "&cYou are already at war!");
        COMMAND_CANNOT_ATTACK_AS_NEUTRAL = config.getString("command-cannot-attack-as-neutral", "&cA neutral nation cannot go to war");
        COMMAND_CANNOT_ATTACK_NEUTRAL = config.getString("command-cannot-attack-neutral", "&cA neutral nation cannot be attacked");
        COMMAND_CANNOT_ATTACK_NOT_ENOUGH_DEFENDERS_ONLINE = config.getString("command-cannot-attack-not-enough-defenders-online", "Attackers must have {PERCENT}% {AND_OR} at least {NUMBER} online to attack");
        COMMAND_CANNOT_ATTACK_YOURSELF = config.getString("command-cannot-attack-yourself", "&cYou cannot attack yourself!");
        COMMAND_HELP = config.getString("command-help", "&bWar command help\n /war list - see current wars\n/war declare [town] - start a war\n/war end [town] - request to end a war peacefully\n/war surrender [town] - avoid combat and forfeit the war");
        COMMAND_MUST_BE_ENEMIES = config.getString("command-must-be-enemies", "&cYou must be a enemy of the defending nation");
        COMMAND_MUST_SPECIFY_TOWN_BECAUSE_MULTIPLE_WARS = config.getString("command-must-specify-towns-because-multiple-wars", "You are in multiple wars, specify a town");
        COMMAND_MUST_SPECIFY_WAR_TO_END = config.getString("command-must-specify-war-to-end", "&cYou must specify a town");
        COMMAND_MUST_SPECIFY_WAR_TO_SURRENDER = config.getString("command-must-specify-war-to-surrender", "&cYou must specify a town");
        COMMAND_NEXT_PEACE_REQUEST_TIME = config.getString("command-next-peace-request-time", "&bYou can send the next peace request in {TIME} seconds");
        COMMAND_NOT_ALLY_OF_DEFENDER_OR_ATTACKER = config.getString("command-not-ally-of-defender-or-attacker", "&cYou must be a nation ally of defender or attacker to join a war");
        COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR = config.getString("command-not-enough-balance-nation-war", "&cYou need {COST} to start a nation war");
        COMMAND_NOT_ENOUGH_BALANCE_TOWN_WAR = config.getString("command-not-enough-balance-town-war", "&cYou need {COST} to start a town war");
        COMMAND_NOT_IN_ANY_WARS = config.getString("command-not-in-any-wars", "&cYou are not in any wars");
        COMMAND_NOT_NATION_WAR = config.getString("command-not-nation-war", "&cYou cannot join a town war");
        COMMAND_NOT_PART_OF_NATION = config.getString("command-not-part-of-nation", "&cYou must be in a nation");
        COMMAND_NOT_PART_OF_TOWN = config.getString("command-not-part-of-town", "&cYou must be in a town");
        COMMAND_NOW_AT_WAR_WITH_NATION = config.getString("command-now-at-war-with-nation", "&bYou are now at war with {NATION}");
        COMMAND_NOW_AT_WAR_WITH_TOWN = config.getString("command-now-at-war-with-town", "&bYou are now at war with {TOWN}");
        COMMAND_NO_SPECIFIED_TOWN = config.getString("command-no-specified-town", "&CYou must specify a town");
        COMMAND_NO_SPECIFIED_WAR = config.getString("command-no-specified-war", "&cUnable to locate war");
        COMMAND_ONLY_CAPITAL_CAN_ATTACK_CAPITAL = config.getString("command-only-capital-can-attack-capital", "&cOnly a capital town can attack a capital town");
        COMMAND_PEACE_REQUEST_SENT = config.getString("command-peace-request-sent", "&bA peace request has been sent");
        COMMAND_TOWN_NOT_FOUND = config.getString("command-town-not-specified", "&cUnable to locate town");
        DEFENDERS_YOU_LOST_NATION_WAR = config.getString("defenders-you-lost-nation-war", "&bYou lost {CLAIMS} for losing the nation war against {ATTACKERS}");
        DEFENDERS_YOU_LOST_TOWN_WAR = config.getString("defenders-you-lost-town-war", "&bYou lost {CLAIMS} for losing the war against {ATTACKERS}");
        DEFENDERS_YOU_WON_NATION_WAR = config.getString("defenders-you-won-nation-war", "&bYou defended your town against {ATTACKERS} and have recieved {COST}");
        DEFENDERS_YOU_WON_TOWN_WAR = config.getString("defenders-you-won-town-war", "&bYou defended your town against {ATTACKERS} and have recieved {COST}");
        DEPOSIT_ERROR = config.getString("deposit-error", "An error occurred while depositing funds");
        NATION_WAR_DEPOSIT = config.getString("nation-war-deposit", "Winning nation war");
        NATION_WAR_JOIN = config.getString("nation-war-join", "Joining nation war");
        NATION_WAR_WITHDRAW = config.getString("nation-war-withdraw", "Starting nation war");
        TOWN_WANTS_PEACE = config.getString("town-wants-peace", "&a{TOWN} wants peace");
        TOWN_WAR_DEPOSIT = config.getString("town-war-deposit", "Winning town war");
        TOWN_WAR_JOIN = config.getString("town-war-join", "Joining town war");
        TOWN_WAR_WITHDRAW = config.getString("town-war-withdraw", "Starting town war");
        WAR_JOINED_AS_ATTACKER = config.getString("war-joined-as-attacker", "&f{NATION} &bhas joined the war to help &f{NATION_DEFENDERS} &bdefend against &f{NATION_ATTACKERS");
        WAR_JOINED_AS_DEFENDER = config.getString("war-joined-as-defender", "&f{NATION} &bhas joined the war to help &f{NATION_ATTACKERS} &bdefeat &f{NATION_DEFENDERS}");
        WAR_LIST_FORMAT = config.getString("war-list-format", "&bAttackers: &f{ATTACKERS} &bDefenders: &f{DEFENDERS}");
        WAR_LIST_HEADER = config.getString("war-list-header", "&bWar list");
        WITHDRAW_ERROR = config.getString("withdraw-error", "&cAn error occurred while withdrawing funds");
    }

    public static void send(CommandSender recipient, String message) {
        if (recipient != null) {
            for (String part : colorize(message).split("\n")) {
                if (part != null && !part.isEmpty()) {
                    recipient.sendMessage(part);
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
