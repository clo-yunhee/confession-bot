package fr.cloyunhee.confessionbot

import discord4j.common.util.Snowflake
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.Embed
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.discordjson.json.*
import discord4j.discordjson.json.gateway.StatusUpdate
import discord4j.rest.util.Color
import discord4j.rest.util.Image
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.NullPointerException
import java.lang.RuntimeException
import kotlin.random.Random

object Main {

    private const val DEFAULT_GUILD_ICON_URL = "https://discord.com/assets/3437c10597c1526c3dbd98c737c2bcae.svg"

    @JvmStatic
    fun main(args: Array<String>) {
        val token = System.getenv("DISCORD_TOKEN")
        val commandName = System.getenv("COMMAND_NAME")
        val channelId = Snowflake.of(System.getenv("CHANNEL_ID"))

        val client = DiscordClientBuilder.create(token).build().login().block() ?: throw Exception("Couldn't instantiate Discord client")

        client.eventDispatcher.on(ReadyEvent::class.java).subscribe { event ->
            println("Logged in as ${event.self.username}#${event.self.discriminator}")

            Presence.online(Activity.playing("DM me !confess to confess"))
                .let(client::updatePresence)
                .subscribe()
        }

        client.eventDispatcher.on(MessageCreateEvent::class.java)
            .filterWhen { event -> event.message.channel.map { it.type == Channel.Type.DM } }
            .filter { event -> event.message.content.startsWith(commandName) }
            .flatMap { event ->
                val content = event.message.content.substring(commandName.length).trim()

                client.getChannelById(channelId).ofType(GuildChannel::class.java).map { channel ->
                    EmbedData.builder().run {
                        val guild = channel.guild.block() ?: throw Exception("Couldn't instantiate guild for channel $channelId")

                        author(EmbedAuthorData.builder()
                            .iconUrl(guild.getIconUrl(Image.Format.WEB_P).orElse(DEFAULT_GUILD_ICON_URL))
                            .name("Anonymous confession")
                            .build())

                        color(Color.of(
                            Random.nextInt(256),
                            Random.nextInt(256),
                            Random.nextInt(256)).rgb)

                        description(content
                            .replace("[", "\\[")
                            .replace("]", "\\]")
                            .replace("(", "\\(")
                            .replace(")", "\\)"))

                        footer(EmbedFooterData.builder()
                            .text("DM me $commandName to confess • Made by Clo#3502")
                            .build())

                        build()
                    }.let { embed ->
                        channel.restChannel.createMessage(embed).subscribe()
                        event.message.restChannel.createMessage("Confession sent!").subscribe()
                    }
                }
            }
            .subscribe()

        client.eventDispatcher.on(MessageCreateEvent::class.java)
            .filterWhen { event -> event.message.channel.map { it.type != Channel.Type.DM } }
            .filter { event -> event.message.content.startsWith(commandName) }
            .flatMap { event -> event.message.restChannel.createMessage("The command must be sent in DMs!") }
            .subscribe()

        client.onDisconnect().block()
    }

}