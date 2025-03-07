package net.harupiza.toropassetc

import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import prj.salmon.toropassicsystem.TOROpassICsystem
import prj.salmon.toropassicsystem.TOROpassICsystemAPI
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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

        // 固定料金徴収
        const val TOLLTYPE_CONSTANT = """^固定:(\d+)$"""
        // 距離換算徴収
        const val TOLLTYPE_DISTANCE = """^距離:(\d+)$"""

        lateinit var toroPassApi: TOROpassICsystemAPI
        lateinit var toroPassIcSystem: TOROpassICsystem
        lateinit var instance: Toropassetc

        var carDistance = HashMap<UUID, Double>()
        private var lastLoc = HashMap<UUID, Location>()
        var riderList = ArrayList<UUID>()
    }

    override fun onEnable() {
        instance = this
        toroPassIcSystem = server.pluginManager.getPlugin("TOROpassICsystem") as TOROpassICsystem
        toroPassApi = TOROpassICsystemAPI(toroPassIcSystem)
        server.pluginManager.registerEvents(this, this)

        logger.info(toroPassIcSystem.name + " has been enabled.")

        server.scheduler.runTaskTimer(this, { ->
            server.onlinePlayers.forEach { player ->
                var block: Block? = null
                var tmpsign: Sign? = null
                for (x in -MAXDISTANCE_OF_ETC..MAXDISTANCE_OF_ETC) {
                    for (z in -MAXDISTANCE_OF_ETC..MAXDISTANCE_OF_ETC) {
                        for (y in -MAXHEIGHT_OF_ETC..MAXHEIGHT_OF_ETC) {
                            val tmpblock = player.location.add(x.toDouble(), y.toDouble(), z.toDouble()).block
                            if (tmpblock.state is Sign) {
                                tmpsign = tmpblock.state as Sign
                                block = tmpblock
                            }
                        }
                    }
                }

                TollProcessor.tryToPass(this, player, block, tmpsign)
            }
        }, 0L, 0L)
    }
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val uuid = event.player.uniqueId

        if (riderList.contains(uuid)) {
            if (carDistance[uuid] == null)
                carDistance[uuid] = 0.0

            carDistance[uuid] = carDistance[uuid]!! + (lastLoc[uuid]?.distance(event.player.location) ?: 0.0)
            lastLoc[uuid] = event.player.location
        }
    }
}
