package base

import base.listeners.ItemClickListener
import base.listeners.PhysicsListener
import base.listeners.PlayerDeathListener
import base.minigames.blueprint_bazaar.BlueprintBazaar
import base.minigames.blueprint_bazaar.BlueprintBazaarCommands
import base.minigames.disco_mayhem.DiscoMayhem
import base.minigames.disco_mayhem.DiscoMayhemCommands
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.HoleInTheWallCommands
import base.minigames.MinigameSkeleton
import base.minigames.maze_hunt.MazeHunt
import base.minigames.maze_hunt.MazeHuntCommands
import base.minigames.parkour_dash.ParkourDash
import base.minigames.parkour_dash.ParkourDashCommands
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale.getDefault

class MinigamePlugin : JavaPlugin() {

    lateinit var discoMayhem: DiscoMayhem
    lateinit var blueprintBazaar: BlueprintBazaar
    lateinit var holeInTheWall: HoleInTheWall
    lateinit var mazeHunt: MazeHunt
    lateinit var parkourDash: ParkourDash

    override fun onEnable() {
        plugin = this

        var listOfMinigames: MutableList<MinigameSkeleton> = mutableListOf()

        discoMayhem = DiscoMayhem(this)
        blueprintBazaar= BlueprintBazaar(this)
        holeInTheWall = HoleInTheWall(this)
        mazeHunt = MazeHunt(this)
        parkourDash = ParkourDash(this)

        listOfMinigames.addAll(listOf(discoMayhem, blueprintBazaar, holeInTheWall, mazeHunt, parkourDash))

        listOfMinigames.forEach {
            it.configMinigame()
        }

        world = server.getWorld("world")!! // Initialize the world object

        //<editor-fold desc="Server Game rule Settings">
        world.setGameRule(GameRule.DO_FIRE_TICK,false)
        world.setGameRule(GameRule.MOB_GRIEFING,false)
        world.difficulty = Difficulty.PEACEFUL
        Bukkit.getServer().onlinePlayers.forEach {
            it.gameMode = GameMode.ADVENTURE
        }
        //</editor-fold>

        //<editor-fold desc="Register the event listeners">
        server.pluginManager.let {
            it.registerEvents(PlayerDeathListener(discoMayhem, holeInTheWall,mazeHunt), this)
            it.registerEvents(PhysicsListener(parkourDash), this)
            it.registerEvents(ItemClickListener(), this)
            it.registerEvents(mazeHunt,this)
        }
        //</editor-fold>

        //<editor-fold desc="Register Command Classes for the minigames">
        getCommand("mg_disco_mayhem")?.setExecutor(
            DiscoMayhemCommands(discoMayhem)
        )
        getCommand("mg_blueprint_bazaar")?.setExecutor(
            BlueprintBazaarCommands(blueprintBazaar)
        )
        getCommand("mg_hole_in_the_wall")?.setExecutor(
            HoleInTheWallCommands(holeInTheWall)
        )
        getCommand("mg_maze_hunt")?.setExecutor(
            MazeHuntCommands(mazeHunt)
        )
        getCommand("mg_parkour_dash")?.setExecutor(
            ParkourDashCommands(parkourDash)
        )
        //</editor-fold>
    }


    override fun onDisable() {
        // Plugin shutdown logic
    }

    fun getSchematicsBaseFolder(minigame: MinigameType): File {
        return when (minigame) {
            MinigameType.BLUEPRINT_BAZAAR -> File(dataFolder, "BlueprintBazaar")
            MinigameType.HOLE_IN_THE_WALL -> File(dataFolder, "HoleInTheWall")
            MinigameType.DISCO_MAYHEM -> File(dataFolder, "DiscoMayhem") //Doesn't exist
            MinigameType.PARKOUR_DASH -> File(dataFolder, "parkourdash")
            MinigameType.MAZE_HUNT -> File(dataFolder, "MazeHunt") //Doesn't exist
        }
    }

    fun getInstanceOfMinigame(minigame: MinigameType): MinigameSkeleton {
        return when (minigame) {
            MinigameType.HOLE_IN_THE_WALL -> this.holeInTheWall
            MinigameType.DISCO_MAYHEM -> this.discoMayhem
            MinigameType.BLUEPRINT_BAZAAR -> this.blueprintBazaar
            MinigameType.PARKOUR_DASH -> this.parkourDash
            MinigameType.MAZE_HUNT -> this.mazeHunt
        }
    }

    companion object {
        lateinit var plugin: MinigamePlugin
        lateinit var world: World

        enum class MinigameType {
            HOLE_IN_THE_WALL,
            DISCO_MAYHEM,
            BLUEPRINT_BAZAAR,
            PARKOUR_DASH,
            MAZE_HUNT;
        }
    }
}
