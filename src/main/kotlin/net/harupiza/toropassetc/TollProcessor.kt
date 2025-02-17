package net.harupiza.toropassetc

import net.harupiza.toropassetc.Toropassetc.Companion.TOLLGATE_CLOSE_COOLDOWN
import net.harupiza.toropassetc.Toropassetc.Companion.TOLLTYPE_CONSTANT
import net.harupiza.toropassetc.Toropassetc.Companion.TOLLTYPE_DISTANCE
import net.harupiza.toropassetc.Toropassetc.Companion.carDistance
import net.harupiza.toropassetc.Toropassetc.Companion.riderList
import net.harupiza.toropassetc.Toropassetc.Companion.soundBell
import net.harupiza.toropassetc.Toropassetc.Companion.toroPassIcSystem
import org.bukkit.ChatColor
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.block.sign.Side
import org.bukkit.entity.Player
import prj.salmon.toropassicsystem.TOROpassICsystem

class TollProcessor {
    companion object {
        // 通過処理(共通化)
        fun tryToPass(plugin: Toropassetc, player: Player, block: Block?, tmpsign: Sign?) {
            val sign = tmpsign ?: return
            val side = if (sign.getSide(Side.FRONT).lines[0] == "[ETC1]") Side.FRONT
            else if (sign.getSide(Side.BACK).lines[0] == "[ETC1]") Side.BACK
            else return
            val uuid = player.uniqueId

            if (sign.getSide(side).lines[3] == "GATE_OPENING") return

            when (sign.getSide(side).lines[1]) {
                "入口", "入口料金所" -> {
                    if (toroPassIcSystem.playerData[uuid]?.isInStation == true) {
                        player.sendMessage(ChatColor.RED.toString() + "既に入場しています。")
                        sign.getSide(side).setLine(3, "GATE_OPENING")
                        sign.update(true, true)
                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
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

                    player.sendMessage(ChatColor.AQUA.toString() + "通過できます。")
                    playGateSound(player, false, plugin)
                    riderList.add(player.uniqueId)

                    sign.getSide(side).setLine(3, "GATE_OPENING")
                    sign.update(true, true)
                    plugin.server.scheduler.runTaskLater(plugin, { ->
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
                        player.sendMessage(ChatColor.RED.toString() + "入場していません。")
                        sign.getSide(side).setLine(3, "GATE_OPENING")
                        sign.update(true, true)
                        plugin.server.scheduler.runTaskLater(plugin, { ->
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

                    processToll(sign.getSide(side).lines[2], playerdata, player, plugin)
                    riderList.remove(player.uniqueId)

                    sign.getSide(side).setLine(3, "GATE_OPENING")
                    sign.update(true, true)
                    plugin.server.scheduler.runTaskLater(plugin, { ->
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
                        player.sendMessage(ChatColor.RED.toString() + "入場していません。")
                        sign.getSide(side).setLine(3, "GATE_OPENING")
                        sign.update(true, true)
                        plugin.server.scheduler.runTaskLater(plugin, { ->
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

                    processToll(sign.getSide(side).lines[2], playerdata, player, plugin)
                    riderList.remove(player.uniqueId)

                    sign.getSide(side).setLine(3, "GATE_OPENING")
                    sign.update(true, true)
                    plugin.server.scheduler.runTaskLater(plugin, { ->
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
        private fun processToll(line: String, playerdata: TOROpassICsystem.StationData, player: Player, plugin: Toropassetc): Boolean {
            if (Regex(TOLLTYPE_CONSTANT).matches(line)) {
                val toll = line.split(":")[1].toIntOrNull()

                if (toll == null) {
                    player.sendMessage(ChatColor.RED.toString() + "構文エラーです。")
                    return false
                }

                playGateSound(player, playerdata.balance-toll < 0, plugin)
                if (playerdata.balance-toll < 0) {
                    player.sendMessage(ChatColor.RED.toString() + "ETCカードが使用できません。")
                    return false
                }
                playerdata.balance -= toll
                player.sendMessage(ChatColor.AQUA.toString() + "通過できます。利用料金は${toll}トロポです。")
                return true
            } else if (Regex(TOLLTYPE_DISTANCE).matches(line)) {
                if (carDistance.containsKey(player.uniqueId)) {
                    val toll = line.split(":")[1].toDoubleOrNull()

                    if (toll == null) {
                        player.sendMessage(ChatColor.RED.toString() + "構文エラーです。")
                        return false
                    }

                    val res = Math.round(carDistance[player.uniqueId]!! * toll)
                    playGateSound(player, playerdata.balance-res < 0, plugin)

                    if (playerdata.balance-res < 0) {
                        player.sendMessage(ChatColor.RED.toString() + "ETCカードが使用できません。")
                        return false
                    }
                    playerdata.balance -= res.toInt()
                    player.sendMessage(ChatColor.AQUA.toString() + "通過できます。利用料金は${res}トロポです。")
                    return true
                }
                player.sendMessage(ChatColor.RED.toString() + "移動履歴がありません（通常時はこのエラーは表示されません）")
                return false
            } else return false
        }

        // ゲート通過音を鳴らす関数
        private fun playGateSound(player: Player, isError: Boolean = false, plugin: Toropassetc) {
            if (isError) {
                player.location.world?.playSound(player, soundBell, 5.0f, 1.5f)
                player.location.world?.playSound(player, soundBell, 5.0f, 2f)
                plugin.server.scheduler.runTaskLater(plugin, { ->
                    player.location.world?.playSound(player, soundBell, 5.0f, 1.5f)
                    player.location.world?.playSound(player, soundBell, 5.0f, 2f)
                }, 5)
                plugin.server.scheduler.runTaskLater(plugin, { ->
                    player.location.world?.playSound(player, soundBell, 5.0f, 1.5f)
                    player.location.world?.playSound(player, soundBell, 5.0f, 2f)
                }, 10)
            } else {
                player.location.world?.playSound(player, soundBell, 5.0f, 1.0f)
                plugin.server.scheduler.runTaskLater(plugin, { ->
                    player.location.world?.playSound(player, soundBell, 5.0f, 0.5f)
                }, 5)
            }
        }
    }
}