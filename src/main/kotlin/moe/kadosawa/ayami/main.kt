@file:JvmName("Main")

package moe.kadosawa.ayami

import dev.minn.jda.ktx.intents
import dev.minn.jda.ktx.light
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.kadosawa.ayami.commands.PingSlash
import moe.kadosawa.ayami.commands.reminder.ReminderAddSlash
import moe.kadosawa.ayami.commands.ResinSlash
import moe.kadosawa.ayami.extensions.*
import moe.kadosawa.ayami.interfaces.SlashExecutor
import moe.kadosawa.ayami.listeners.MainListener
import moe.kadosawa.ayami.tables.Reminders
import moe.kadosawa.ayami.utils.Args
import moe.kadosawa.ayami.utils.Config
import moe.kadosawa.ayami.utils.dataSource
import mu.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.requests.GatewayIntent
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.properties.Delegates
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

val jdaIsReady = CompletableDeferred<Unit>()

val slashData = listOf(
    command("ping", "Sends ping and then ping pong") {
        privacyOption()
    },

    command("resin", "Calculate when you'll have specified amount of resin") {
        option(OptionType.INTEGER, "current", "Your current amount of resin") {
            setRequiredRange(0, 159)
        }
        option(OptionType.INTEGER, "needed", "Amount of resin you need") {
            setRequiredRange(1, 160)
        }
        privacyOption()
    },

    command("reminder", "Manage reminders") {
        subcommandData("add", "Create a new reminder") {
            option(OptionType.STRING, "duration", "ISO-8601 duration format")
            option(OptionType.STRING, "content", "Message that you will receive")
            privacyOption()
        }
    }
)

var slashExecutors: MutableMap<String, SlashExecutor> by Delegates.notNull()
var jda: JDA by Delegates.notNull()

private suspend fun onceReady() {
    // Wait for the ready event
    jdaIsReady.await()

    if (Args.refreshSlash) {
        // Re-add the commands
        jda.updateCommands()
            .addCommands(slashData)
            .await()

        jda.getGuildById("911222786968674334")!!
            .updateCommands()
            .addCommands(slashData)
            .await()

        logger.info { "Global and debug guild commands were re-added!" }
    }
}

fun main(args: Array<String>) = runBlocking<Unit> {
    Args.parser.parse(args)
    Config.fromFile(Args.configPath)

    Database.connect(dataSource)

    if (Args.dbInit) {
        newSuspendedTransaction {
            if (!Reminders.exists()) {
                SchemaUtils.create(Reminders)
            }
        }

        logger.info { "Tables were created" }
        exitProcess(0)
    }

    // Create jda instance
    jda = light(Config.discordToken) {
        intents += GatewayIntent.GUILD_MEMBERS
        intents += GatewayIntent.DIRECT_MESSAGES
        intents += GatewayIntent.GUILD_MESSAGES

        addEventListeners(MainListener())
    }

    // Create commands
    slashExecutors = mutableMapOf(
        "ping" to PingSlash(),
        "resin" to ResinSlash(),
        "reminder/add" to ReminderAddSlash()
    )

    // Suspended function awaiting
    // completion of jdaIsReady
    launch { onceReady() }
}
