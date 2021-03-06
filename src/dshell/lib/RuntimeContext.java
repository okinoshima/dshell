package dshell.lib;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.net.SyslogAppender;
import org.apache.log4j.varia.NullAppender;

import zen.util.LibZen;

public class RuntimeContext implements Serializable {
	private static final long serialVersionUID = -2807505115721639912L;
	private final static String defaultPattern = "%d{ISO8601}: %m%n";
	public static enum AppenderType {
		empty,
		file,
		stdout,
		stderr,
		syslog;
	}

	// logging configuration
	transient private final Logger logger;
	transient private Layout defaultLayout;
	private AppenderType appenderType;
	private String[] appenderOptions;

	// debug mode
	private boolean debugMode = false;

	// working directory
	transient private String workingDir;

	// environmental variable
	transient private final TreeSet<String> envSet;

	private RuntimeContext(){
		if(System.getProperty("os.name").startsWith("Windows")) {
			Utils.fatal(1, "Windows is Not Supported");
		}
		// init logger
		this.defaultLayout = new PatternLayout(defaultPattern);
		this.appenderType = AppenderType.empty;
		this.appenderOptions = new String[] {"empty"};
		this.logger = Logger.getLogger(RuntimeContext.class);
		BasicConfigurator.configure(new NullAppender());
		// get working dir
		this.workingDir = System.getProperty("user.dir");
		this.envSet = new TreeSet<String>();
		Map<String, String> envMap = System.getenv();
		for(Map.Entry<String, String> entry : envMap.entrySet()) {
			this.envSet.add(entry.getKey());
		}
	}

	public Logger getLogger() {
		return this.logger;
	}

	public void changeAppender(AppenderType type, String... options) {
		this.appenderType = type;
		this.appenderOptions = new String[options.length];
		System.arraycopy(options, 0, this.appenderOptions, 0, options.length);
		BasicConfigurator.resetConfiguration();
		Appender appender = new NullAppender();
		switch(this.appenderType) {
		case empty:
			appender = new NullAppender();
			break;
		case file:
			String fileName = this.appenderOptions[0];
			if(fileName.startsWith("~")) {
				fileName = Utils.getEnv("HOME") + fileName.substring(1); 
			}
			try {
				appender = new FileAppender(this.defaultLayout, fileName, true);
			}
			catch (IOException e) {
				Utils.fatal(1, "not found log file: " + this.appenderOptions[0]);
			}
			break;
		case stdout:
			appender = new ConsoleAppender(this.defaultLayout, "System.out");
			break;
		case stderr:
			appender = new ConsoleAppender(this.defaultLayout, "System.err");
			break;
		case syslog:
			String host = "localhost";
			if(this.appenderOptions.length > 0) {
				host = this.appenderOptions[0];
			}
			appender = new SyslogAppender(this.defaultLayout, host, SyslogAppender.LOG_LOCAL0);
			break;
		default:
			break;
		}
		BasicConfigurator.configure(appender);
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
		LibZen.DebugMode = debugMode;
	}

	public boolean isDebugMode() {
		return this.debugMode;
	}

	public String getWorkingDirectory() {
		return this.workingDir;
	}

	public String getNewWorkingDirectory() {
		int size = 256;
		byte[] buffers = new byte[size];
		CLibraryWrapper.INSTANCE.getcwd(buffers, size);
		int actualSize = 0;
		for(byte buf : buffers) {
			if(buf == 0) {
				break;
			}
			actualSize++;
		}
		byte[] newBuffers = new byte[actualSize];
		for(int i = 0; i < actualSize; i++) {
			newBuffers[i] = buffers[i];
		}
		return new String(newBuffers);
	}

	public int changeDirectory(String path) {
		String targetPath = path;
		if(path.equals("")) {
			targetPath = Utils.getEnv("HOME");
		}
		int status = CLibraryWrapper.INSTANCE.chdir(targetPath);
		if(status == -1) {
			this.perror("-dshell: cd");
		}
		else {
			this.workingDir = this.getNewWorkingDirectory();
		}
		return status;
	}

	@SuppressWarnings("rawtypes")
	public SortedSet getEnvSet() {
		return Collections.unmodifiableSortedSet(this.envSet);
	}

	private void perror(String message) {
		CLibraryWrapper.INSTANCE.perror(message);
	}

	public int setenv(String key, String env, boolean override) {
		int ret = CLibraryWrapper.INSTANCE.setenv(key, env, override ? 0 : 1);
		if(ret == 0) {
			this.envSet.add(key);
		}
		return ret;
	}

	public String getenv(String key) {
		return CLibraryWrapper.INSTANCE.getenv(key);
	}

	private static class ContextHolder {
		private static final RuntimeContext context = new RuntimeContext();
	}

	public static RuntimeContext getContext() {
		return ContextHolder.context;
	}

	public static void loadContext(RuntimeContext otherContext) {
		ContextHolder.context.changeAppender(otherContext.appenderType, otherContext.appenderOptions);
		ContextHolder.context.setDebugMode(otherContext.isDebugMode());
	}
}

interface CLibraryWrapper extends com.sun.jna.Library {
	CLibraryWrapper INSTANCE = (CLibraryWrapper) com.sun.jna.Native.loadLibrary("c", CLibraryWrapper.class);

	int chdir(String path);
	String getcwd(byte[] buf, int size);
	void perror(String s);
	int setenv(String key, String env, int override);
	String getenv(String key);
}
