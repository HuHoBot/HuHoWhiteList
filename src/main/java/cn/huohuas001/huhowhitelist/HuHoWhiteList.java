package cn.huohuas001.huhowhitelist;

// 数据库相关
import java.sql.*;

// Bukkit 相关
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

// 其他第三方
import cn.huohuas001.huhobot.spigot.api.BotCustomCommand;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class HuHoWhiteList extends JavaPlugin implements Listener {
    private Logger logger; //Logger
    private static TaskScheduler scheduler;
    private static HuHoWhiteList plugin; //插件对象
    private Connection connection;

    /**
     * 获取插件
     *
     * @return 插件对象
     */
    public static HuHoWhiteList getPlugin() {
        return plugin;
    }

    @Override
    public void onEnable() {
        logger = getLogger();
        plugin = this;
        scheduler = UniversalScheduler.getScheduler(this);

        if (this.getServer().getPluginManager().getPlugin("HuHoBot") == null)
        {
            this.getLogger().severe("HuHoBot is not installed. Disabling...");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Plugin huhoBot = getServer().getPluginManager().getPlugin("HuHoBot");
        if (huhoBot == null) {
            getLogger().severe("HuHoBot 未安装");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            Class.forName("cn.huohuas001.huhobot.spigot.api.BotCustomCommand");
        } catch (ClassNotFoundException e) {
            logger.severe("无法加载 BotCustomCommand 类：" + e.getMessage());
        }

        this.getServer().getPluginManager().registerEvents(this, huhoBot);

        this.saveDefaultConfig();

        initDatabase();

        logger.info("HuHoWhiteList Loaded. By HuoHuas001");
    }

    /**
     * 获取计划对象
     *
     * @return 计划对象
     */
    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    private void initDatabase() {
        try {
            Class.forName("cn.huohuas001.shaded.h2.Driver");

            // 获取跨平台路径
            String dbPath = getDataFolder().getAbsolutePath().replace('\\', '/');
            // 使用兼容性参数
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + dbPath + "/bindings;" +
                            "DB_CLOSE_DELAY=-1;" +
                            "DATABASE_TO_UPPER=false;" // 禁用自动转大写
            );
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS bindings (" +
                        "qq VARCHAR(20) PRIMARY KEY," +
                        "player_name VARCHAR(16) NOT NULL UNIQUE)");
            }
        } catch (Exception e) {
            logger.severe("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 绑定校验方法
    private boolean isAlreadyBound(String qq, String playerName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM bindings WHERE qq=? OR player_name=?")) {
            ps.setString(1, qq);
            ps.setString(2, playerName);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            logger.warning("数据库查询异常: " + e.getMessage());
            return true; // 异常时阻止操作
        }
    }

    // 存储绑定方法
    private void saveBinding(String qq, String playerName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO bindings (qq, player_name) VALUES (?, ?)")) {
            ps.setString(1, qq);
            ps.setString(2, playerName);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("数据存储失败: " + e.getMessage());
        }
    }

    private int deleteBinding(String identifier) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM bindings WHERE qq=? OR player_name=?")) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("数据删除失败: " + e.getMessage());
            return -1;
        }
    }

    // 根据QQ号获取玩家名
    public String getNameByQQ(String qq) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_name FROM bindings WHERE qq = ?")) {
            ps.setString(1, qq);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("player_name") : null;
            }
        } catch (SQLException e) {
            logger.warning("QQ号查询玩家名失败: " + e.getMessage());
            return null;
        }
    }

    // 根据玩家名获取QQ号
    public String getQQByName(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT qq FROM bindings WHERE player_name = ?")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("qq") : null;
            }
        } catch (SQLException e) {
            logger.warning("玩家名查询QQ号失败: " + e.getMessage());
            return null;
        }
    }

    public Map<String, String> getBindingInfo(String identifier) {
        Map<String, String> result = new HashMap<>();

        // 先尝试作为QQ查询
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT qq, player_name FROM bindings WHERE qq = ?")) {
            ps.setString(1, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("qq", rs.getString("qq"));
                    result.put("playerName", rs.getString("player_name"));
                    return result;
                }
            }
        } catch (SQLException e) {
            //logger.warning("QQ查询异常: " + e.getMessage());
        }

        // 若未找到，再尝试作为玩家名查询
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT qq, player_name FROM bindings WHERE player_name = ?")) {
            ps.setString(1, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("qq", rs.getString("qq"));
                    result.put("playerName", rs.getString("player_name"));
                }
            }
        } catch (SQLException e) {
            //logger.warning("玩家名查询异常: " + e.getMessage());
        }

        return result.isEmpty() ? null : result;
    }



    private String getLanguage(String LanguageKey){
        String lang = this.getConfig().getString("language."+LanguageKey);
        if(lang == null || lang.isEmpty()){
            logger.severe("HuHoWhiteList语言文件配置错误.");
            return "语言文件配置错误.";
        }
        lang.replace("\\n", "\n");
        return lang;
    }

    @EventHandler
    public void onCommandSend(BotCustomCommand event)
    {
        this.reloadConfig();
        String applyKeyWord = this.getConfig().getString("keyWord.apply");
        String deleteKeyWord = this.getConfig().getString("keyWord.delete");

        if(applyKeyWord != null && event.getCommand().equals(applyKeyWord)){ //申请白名单
            event.setCancelled(true);

            String addCommand = this.getConfig().getString("whiteList.add");
            if(addCommand == null){
                logger.severe("配置出错，请修改config.yml后重试：添加白名单指令为空");
                event.respone("配置出错，请修改config.yml后重试：添加白名单指令为空", "error");
                return;
            }

            String playerName = event.getParam().get(0);
            JSONObject data = event.getData();
            String qqNum = null;

            if(data != null && data.containsKey("author")) {
                JSONObject author = data.getJSONObject("author");
                if(author != null && author.containsKey("bindQQ")) {
                    qqNum = author.getString("bindQQ");
                }
            }

            if(qqNum == null || qqNum.isEmpty()) {
                logger.warning("无法获取 QQ 绑定信息");
                event.respone(getLanguage("noBind"), "error");
                return;
            }
            //logger.info(event.getData().toString());

            if(playerName != null && !playerName.isEmpty()){
                //检测该账号是否绑定过
                if(isAlreadyBound(qqNum, playerName)){
                    event.respone(getLanguage("alreadyBind"), "error");
                    return;
                }
                addCommand = addCommand.replace("{name}", event.getParam().get(0));
                String ret = ServerManager.sendCmd(addCommand, true, true);
                event.respone(getLanguage("execute").replace("{ret}",ret), "success");
                saveBinding(qqNum, playerName);
            }else{
                event.respone(getLanguage("noPlayerName"), "error");
            }
        }
        else if(deleteKeyWord != null && event.getCommand().equals(deleteKeyWord)){//取消白名单
            event.setCancelled(true);

            if(!event.isRunByAdmin()){
                event.respone(getLanguage("noAdmin"), "error");
            }

            String deleteCommand = this.getConfig().getString("whiteList.del");
            if(deleteCommand == null){
                logger.severe("配置出错，请修改config.yml后重试：删除白名单指令为空");
                event.respone("配置出错，请修改config.yml后重试：删除白名单指令为空", "error");
                return;
            }

            // 获取参数
            String param = event.getParam().size() > 0 ? event.getParam().get(0) : null;
            if(param == null || param.isEmpty()){
                event.respone(getLanguage("noParam"), "error");
                return;
            }

            // 获取绑定信息
            Map<String, String> binding = getBindingInfo(param);
            if(binding == null){
                event.respone(getLanguage("deleteNotFound").replace("{data}", param), "error");
                return;
            }

            // 执行删除（同时支持QQ或玩家名作为条件）
            int deleted = deleteBinding(param);
            if(deleted > 0){
                // 同步删除服务器白名单（使用玩家名）
                String cmd = deleteCommand.replace("{name}", binding.get("playerName"));
                ServerManager.sendCmd(cmd, true, false);

                // 返回详细信息
                event.respone(getLanguage("deleteSuccess")
                        .replace("{player}", binding.get("playerName"))
                        .replace("{qq}", binding.get("qq")), "success");
            }else{
                event.respone(getLanguage("deleteFail"), "error");
            }
        }
    }

}
