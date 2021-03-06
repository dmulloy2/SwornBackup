/**
 * (c) 2015 dmulloy2
 */
package net.dmulloy2.swornbackup;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.DAY_OF_YEAR;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bukkit.plugin.Plugin;

/**
 * @author dmulloy2
 */

public class BackupHandler {
	private final File backupFolder;
	private final File datedFolder;

	private final SwornBackup plugin;
	public BackupHandler(SwornBackup plugin) {
		this.plugin = plugin;

		if (!plugin.getConfig().isConfigurationSection("backupFolder")) {
			plugin.getConfig().set("backupFolder", plugin.getDataFolder().getAbsolutePath());
			plugin.saveConfig();
		}

		this.backupFolder = new File(plugin.getConfig().getString("backupFolder"));

		Calendar cal = Calendar.getInstance();
		// Calendar#get(MONTH) is off by 1. Thank you, C.
		String date = (cal.get(MONTH) + 1) + "-" + cal.get(DAY_OF_MONTH) + "-" + cal.get(YEAR);
		this.datedFolder = new File(backupFolder, date);
		this.datedFolder.mkdirs();
	}

	public void checkBackups() {
		for (String pluginName : plugin.getConfig().getStringList("backupPlugins")) {
			Plugin backup = plugin.getServer().getPluginManager().getPlugin(pluginName);
			if (backup != null) {
				if (!new File(datedFolder, pluginName + ".zip").exists()) {
					makeBackup(backup);
				}
			} else {
				plugin.getLogger().info(MessageFormat.format("Could not find plugin {0}!", pluginName));
			}

			cleanBackups();
		}
	}

	private void makeBackup(Plugin backup) {
		plugin.getLogger().info(MessageFormat.format("Backing up plugin {0}...", backup.getName()));

		File backupFolder = new File(datedFolder, backup.getName());
		File dataFolder = backup.getDataFolder();

		try {
			zip(dataFolder, new File(backupFolder + ".zip"));
		} catch (Throwable ex) {
			plugin.getLogger().info(MessageFormat.format("Could not backup {0}: {1}", backup.getName(), ex));
			return;
		}

		plugin.getLogger().info(MessageFormat.format("{0} successfully backed up.", backup.getName()));
	}

	private void cleanBackups() {
		for (File folder : backupFolder.listFiles()) {
			if (! folder.isDirectory()) {
				continue;
			}

			String name = folder.getName();
			Calendar cal = Calendar.getInstance();
			String[] date = name.split("-");

			try {
				int month = Integer.parseInt(date[0]) - 1; // Correct our aesthetic fix
				int day = Integer.parseInt(date[1]);
				int year = Integer.parseInt(date[2]);
				cal.set(year, month, day);
			} catch (NumberFormatException ex) {
				plugin.getLogger().log(Level.WARNING, "Failed to determine date of backup " + folder, ex);
			}

			if ((cal.get(DAY_OF_YEAR) % 7 != 2) && (Calendar.getInstance().get(DAY_OF_YEAR) - cal.get(DAY_OF_WEEK) > 7))
				deleteTree(folder);
		}
	}

	private void deleteTree(File tree) {
		if (tree == null) {
			return;
		}

		for (File file : tree.listFiles()) {
			if (file.isDirectory()) {
				deleteTree(file);
				file.delete();
			} else {
				file.delete();
			}
		}

		tree.delete();
	}

	private void zip(File directory, File zipFile) throws IOException {
		URI base = directory.toURI();
		Deque<File> queue = new LinkedList<File>();
		queue.push(directory);
		OutputStream out = new FileOutputStream(zipFile);
		try (ZipOutputStream zout = new ZipOutputStream(out)) {
			while (!queue.isEmpty()) {
				directory = queue.pop();
				for (File kid : directory.listFiles()) {
					String name = base.relativize(kid.toURI()).getPath();
					if (kid.isDirectory()) {
						queue.push(kid);
						zout.putNextEntry(new ZipEntry(name));
					} else {
						zout.putNextEntry(new ZipEntry(name));
						copy(kid, zout);
						zout.closeEntry();
					}
				}
			}
		}
	}

	private void copy(File file, OutputStream out) throws IOException {
		try (InputStream in = new FileInputStream(file)) {
			byte[] buffer = new byte[1024];
			while (true) {
				int readCount = in.read(buffer);
				if (readCount < 0)
					break;
				out.write(buffer, 0, readCount);
			}
		}
	}
}