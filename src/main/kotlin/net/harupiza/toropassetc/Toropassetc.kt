package net.harupiza.toropassetc

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.block.sign.Side
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import prj.salmon.toropassicsystem.TOROpassICsystem

class Toropassetc : JavaPlugin(), Listener {
    companion object {
        // ゲートが閉まるまでのクールダウンタイム、60ティック=3秒
        const val TOLLGATE_CLOSE_COOLDOWN = 60L
        // ETC機能看板からのY座標のMAX距離
        const val MAXHEIGHT_OF_ETC = 15
        // ETC機能看板からのX,Z座標のMAX距離
        const val MAXDISTANCE_OF_ETC = 1
        // 通過音
        val soundBell: Sound = Sound.BLOCK_NOTE_BLOCK_BELL

        // 固定料金徴収のETC機能看板の正規表現
        const val TOLLTYPE_CONSTANT = """^固定:(\d+)$"""
    }

    private lateinit var toroPassIcSystem: TOROpassICsystem

    override fun onEnable() {
        toroPassIcSystem = server.pluginManager.getPlugin("TOROpassICsystem") as TOROpassICsystem
        server.pluginManager.registerEvents(this, this)

        logger.info(toroPassIcSystem.name + " has been enabled.")
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        var block: Block? = null
        var tmpsign: Sign? = null
        for (x in -MAXDISTANCE_OF_ETC..MAXDISTANCE_OF_ETC) {
            for (z in -MAXDISTANCE_OF_ETC..MAXDISTANCE_OF_ETC) {
                for (y in -MAXHEIGHT_OF_ETC..MAXHEIGHT_OF_ETC) {
                    val tmpblock = event.player.location.add(x.toDouble(), y.toDouble(), z.toDouble()).block
                    if (tmpblock.state is Sign) {
                        tmpsign = tmpblock.state as Sign
                        block = tmpblock
                    }
                }
            }
        }

        val sign = tmpsign ?: return
        // Kotlinのココが好き
        val side = if (sign.getSide(Side.FRONT).lines[0] == "[ETC1]") Side.FRONT
            else if (sign.getSide(Side.BACK).lines[0] == "[ETC1]") Side.BACK
            else return
        val uuid = event.player.uniqueId

        if (sign.getSide(side).lines[3] == "GATE_OPENING") return

        when (sign.getSide(side).lines[1]) {
            "入口", "入口料金所" -> {
                if (toroPassIcSystem.playerData[uuid]?.isInStation == true) {
                    event.player.sendMessage(ChatColor.RED.toString() + "既に入場しています。")
                    sign.getSide(side).setLine(3, "GATE_OPENING")
                    sign.update(true, true)
                    server.scheduler.runTaskLater(this, { ->
                        // Blockじゃないと更新できなかったです
                        if (block != null && block.state is Sign) {
                            val sign2 = block.state as Sign
                            sign2.getSide(Side.FRONT).setLine(3, "")
                            sign2.getSide(Side.BACK).setLine(3, "")
                            sign2.update(true, true)
                        }
                    }, TOLLGATE_CLOSE_COOLDOWN)
                    return
                }
                (if (toroPassIcSystem.playerData[uuid] != null) toroPassIcSystem.playerData[uuid] else
                    run {
                        toroPassIcSystem.playerData[uuid] = toroPassIcSystem.StationData()
                        toroPassIcSystem.playerData[uuid]
                    })?.enterStation("ETC1")

                event.player.sendMessage(ChatColor.AQUA.toString() + "通過できます。")
                playGateSound(event.player)

                sign.getSide(side).setLine(3, "GATE_OPENING")
                sign.update(true, true)
                server.scheduler.runTaskLater(this, { ->
                    // Blockじゃないと更新できなかったです
                    if (block != null && block.state is Sign) {
                        val sign2 = block.state as Sign
                        sign2.getSide(Side.FRONT).setLine(3, "")
                        sign2.getSide(Side.BACK).setLine(3, "")
                        sign2.update(true, true)
                    }
                }, TOLLGATE_CLOSE_COOLDOWN)
            }
            "出口", "出口料金所" -> {
                val playerdata = toroPassIcSystem.playerData[uuid] ?: return

                if (!playerdata.isInStation) {
                    event.player.sendMessage(ChatColor.RED.toString() + "入場していません。")
                    sign.getSide(side).setLine(3, "GATE_OPENING")
                    sign.update(true, true)
                    server.scheduler.runTaskLater(this, { ->
                        // Blockじゃないと更新できなかったです
                        if (block != null && block.state is Sign) {
                            val sign2 = block.state as Sign
                            sign2.getSide(Side.FRONT).setLine(3, "")
                            sign2.getSide(Side.BACK).setLine(3, "")
                            sign2.update(true, true)
                        }
                    }, TOLLGATE_CLOSE_COOLDOWN)
                    return
                }
                playerdata.exitStation()

                processToll(sign.getSide(side).lines[2], playerdata, event.player)

                sign.getSide(side).setLine(3, "GATE_OPENING")
                sign.update(true, true)
                server.scheduler.runTaskLater(this, { ->
                    // Blockじゃないと更新できなかったです
                    if (block != null && block.state is Sign) {
                        val sign2 = block.state as Sign
                        sign2.getSide(Side.FRONT).setLine(3, "")
                        sign2.getSide(Side.BACK).setLine(3, "")
                        sign2.update(true, true)
                    }
                }, TOLLGATE_CLOSE_COOLDOWN)
            }
            "本線", "本線料金所" -> {
                val playerdata = toroPassIcSystem.playerData[uuid] ?: return

                if (!playerdata.isInStation) {
                    event.player.sendMessage(ChatColor.RED.toString() + "入場していません。")
                    sign.getSide(side).setLine(3, "GATE_OPENING")
                    sign.update(true, true)
                    server.scheduler.runTaskLater(this, { ->
                        // Blockじゃないと更新できなかったです
                        if (block != null && block.state is Sign) {
                            val sign2 = block.state as Sign
                            sign2.getSide(Side.FRONT).setLine(3, "")
                            sign2.getSide(Side.BACK).setLine(3, "")
                            sign2.update(true, true)
                        }
                    }, TOLLGATE_CLOSE_COOLDOWN)
                    return
                }

                processToll(sign.getSide(side).lines[2], playerdata, event.player)

                sign.getSide(side).setLine(3, "GATE_OPENING")
                sign.update(true, true)
                server.scheduler.runTaskLater(this, { ->
                    // Blockじゃないと更新できなかったです
                    if (block != null && block.state is Sign) {
                        val sign2 = block.state as Sign
                        sign2.getSide(Side.FRONT).setLine(3, "")
                        sign2.getSide(Side.BACK).setLine(3, "")
                        sign2.update(true, true)
                    }
                }, TOLLGATE_CLOSE_COOLDOWN)
            }
        }
    }

    // 料金計算等をする関数
    private fun processToll(line: String, playerdata: TOROpassICsystem.StationData, player: Player): Boolean {
        if (Regex(TOLLTYPE_CONSTANT).matches(line)) {
            val toll = (line.split(":")[1].toIntOrNull() ?: return false)
            playGateSound(player, playerdata.balance-toll < 0)
            if (playerdata.balance-toll < 0) {
                player.sendMessage(ChatColor.RED.toString() + "ETCカードが使用できません。")
                return false
            }
            playerdata.balance -= toll
            player.sendMessage(ChatColor.AQUA.toString() + "通過できます。利用料金は${toll}トロポです。")
            return true
        } else return false
    }

    // ゲート通過音を鳴らす関数
    private fun playGateSound(player: Player, isError: Boolean = false) {
        if (isError) {
            player.location.world?.playSound(player, soundBell, 5.0f, 1.5f)
            player.location.world?.playSound(player, soundBell, 5.0f, 2f)
            server.scheduler.runTaskLater(this, { ->
                player.location.world?.playSound(player, soundBell, 5.0f, 1.5f)
                player.location.world?.playSound(player, soundBell, 5.0f, 2f)
            }, 5)
            server.scheduler.runTaskLater(this, { ->
                player.location.world?.playSound(player, soundBell, 5.0f, 1.5f)
                player.location.world?.playSound(player, soundBell, 5.0f, 2f)
            }, 10)
        } else {
            player.location.world?.playSound(player, soundBell, 5.0f, 1.0f)
            server.scheduler.runTaskLater(this, { ->
                player.location.world?.playSound(player, soundBell, 5.0f, 0.5f)
            }, 5)
        }
    }
}
