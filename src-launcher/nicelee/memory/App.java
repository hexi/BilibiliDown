package nicelee.memory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Optional;

import nicelee.memory.classloader.MemoryClassLoader;
import nicelee.memory.url.MemoryURLHandler;

public class App {

	public static void main(String[] args) throws FileNotFoundException, IOException, URISyntaxException {
		final ProtectionDomain pd = App.class.getProtectionDomain();
		final File launchJarFile = new File(pd.getCodeSource().getLocation().toURI());
		final File coreJarFile = new File(launchJarFile.getParentFile(), "INeedBiliAV.jar");
//		final File coreJarFile = new File("D:\\Workspace\\javaweb-springboot\\Demo\\test\\INeedBiliAV.jar");
//		final File coreJarFile = new File("D:\\Workspace\\javaweb-springboot\\Demo\\JarClassLoader-main\\INeedBiliAV.jar");
//		final File coreJarFile = new File("D:\\Workspace\\javaweb-springboot\\BilibiliDown\\release\\INeedBiliAV.jar");

		MemoryURLHandler.addSource("INeedBiliAV.jar", readAllBytes(coreJarFile));
		MemoryURLHandler.lockMemory();

		System.out.println(pd.getCodeSource().getLocation().getPath());
		MemoryClassLoader mcl = new MemoryClassLoader(pd);
		try {
			Class<?> clazz = mcl.loadClass("nicelee.ui.FrameMain");
			Method method = clazz.getMethod("main", new Class<?>[] { String[].class });
			method.invoke(null, (Object) args);
		} catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
	}

	private static byte[] readAllBytes(File f) throws IOException {
		InputStream in = new FileInputStream(f);
		long fileSize = f.length();
		byte[] buf = new byte[(int) fileSize];
		int len = in.read(buf);
		in.close();
		if (len != fileSize) {
			throw new IOException("File length is not correct! fileSize:" + fileSize);
		}
		return buf;
	}

	public static final String SUN_JAVA_COMMAND = "sun.java.command";

	/**
	 * Restart the current Java application
	 * See https://dzone.com/articles/programmatically-restart-java
	 * @throws IOException
	 */
	public static void restartApplication() throws IOException {
		try {
			// java binary
			String java = null;
			try {
				// java 8 没有这个实现
				// Optional<String> command = ProcessHandle.current().info().command();
				Class<?> phCls = Class.forName("java.lang.ProcessHandle");
				Object phInstance = phCls.getDeclaredMethod("current").invoke(null);
				Object phInfo = phInstance.getClass().getInterfaces()[0].getDeclaredMethod("info").invoke(phInstance);
				Object phCommand = phInfo.getClass().getInterfaces()[0].getDeclaredMethod("command").invoke(phInfo);
				@SuppressWarnings("unchecked")
				Optional<String> command = (Optional<String>) phCommand;
				java = command.get();
			} catch (Throwable e) {
				String javaHome = System.getProperty("java.home");
				java = javaHome != null ? javaHome + "/bin/java" : "java";
			}
			// vm arguments
			List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
			StringBuilder vmArgsOneLine = new StringBuilder();
			for (String arg : vmArguments) {
				// if it's the agent argument : we ignore it otherwise the
				// address of the old application and the new one will be in conflict
				if (!arg.contains("-agentlib")) {
					vmArgsOneLine.append(arg);
					vmArgsOneLine.append(" ");
				}
			}
			// init the command to execute, add the vm args
			final StringBuilder cmd = new StringBuilder("\"" + java + "\" " + vmArgsOneLine);

			// program main and program arguments
			String[] mainCommand = System.getProperty(SUN_JAVA_COMMAND).split(" ");
			// program main is a jar
			if (mainCommand[0].endsWith(".jar")) {
				// if it's a jar, add -jar mainJar
				cmd.append("-jar \"" + new File(mainCommand[0]).getPath() + "\"");
			} else {
				// else it's a .class, add the classpath and mainClass
				cmd.append("-cp \"" + System.getProperty("java.class.path") + "\" " + mainCommand[0]);
			}
			// finally add program arguments
			for (int i = 1; i < mainCommand.length; i++) {
				cmd.append(" ");
				cmd.append(mainCommand[i]);
			}
			// execute the command in a shutdown hook, to be sure that all the
			// resources have been disposed before restarting the application
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					System.out.println(cmd.toString());
					try {
						Runtime.getRuntime().exec(cmd.toString());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			// exit
			System.exit(0);
		} catch (Exception e) {
			// something went wrong
			throw new IOException("Error while trying to restart the application", e);
		}
	}
}
