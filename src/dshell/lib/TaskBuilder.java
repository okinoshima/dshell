package dshell.lib;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;

import dshell.lang.DShellGrammar;
import dshell.util.Utils;

import static dshell.lib.TaskOption.Behavior.returnable;
import static dshell.lib.TaskOption.Behavior.printable ;
import static dshell.lib.TaskOption.Behavior.throwable ;
import static dshell.lib.TaskOption.Behavior.background;
import static dshell.lib.TaskOption.Behavior.tracable ;

import static dshell.lib.TaskOption.RetType.VoidType   ;
import static dshell.lib.TaskOption.RetType.IntType    ;
import static dshell.lib.TaskOption.RetType.BooleanType;
import static dshell.lib.TaskOption.RetType.StringType ;
import static dshell.lib.TaskOption.RetType.TaskType   ;

public class TaskBuilder {
	private TaskOption option;
	private PseudoProcess[] Processes;
	private long timeout = -1;
	private StringBuilder sBuilder;

	public MessageStreamHandler stdoutHandler;
	public MessageStreamHandler stderrHandler;

	public TaskBuilder(ArrayList<ArrayList<String>> cmdsList, TaskOption option) {
		this.option = option;
		ArrayList<ArrayList<String>> newCmdsList = this.setInternalOption(cmdsList);
		this.Processes = this.createProcs(newCmdsList);
		// generate object representation
		this.sBuilder = new StringBuilder();
		for(int i = 0; i< this.Processes.length; i++) {
			if(i != 0) {
				this.sBuilder.append(",\n");
			}
			this.sBuilder.append("{");
			this.sBuilder.append(this.Processes[i].toString());
			this.sBuilder.append("}");
		}
		this.sBuilder.append("\n<");
		this.sBuilder.append(this.option.toString());
		this.sBuilder.append(">");
	}

	public Object invoke() {
		Task task = new Task(this);
		if(this.option.is(background)) {
			return (this.option.isRetType(TaskType) && this.option.is(returnable)) ? task : null;
		}
		task.join();
		if(this.option.is(returnable)) {
			if(this.option.isRetType(StringType)) {
				return task.getOutMessage();
			}
			else if(this.option.isRetType(BooleanType)) {
				return new Boolean(task.getExitStatus() == 0);
			}
			else if(this.option.isRetType(IntType)) {
				return new Integer(task.getExitStatus());
			}
			else if(this.option.isRetType(TaskType)) {
				return task;
			}
		}
		return null;
	}

	public PseudoProcess[] getProcesses() {
		return this.Processes;
	}

	public TaskOption getOption() {
		return this.option;
	}

	public long getTimeout() {
		return this.timeout;
	}

	@Override public String toString() {
		return this.sBuilder.toString();
	}

	private ArrayList<ArrayList<String>> setInternalOption(ArrayList<ArrayList<String>> cmdsList) {
		boolean enableTrace = false;
		ArrayList<ArrayList<String>> newCmdsBuffer = new ArrayList<ArrayList<String>>();
		for(ArrayList<String> currentCmds : cmdsList) {
			if(currentCmds.get(0).equals(DShellGrammar.timeout)) {
				StringBuilder numBuilder = new StringBuilder();
				StringBuilder unitBuilder = new StringBuilder();
				int len = currentCmds.get(1).length();
				for(int j = 0; j < len; j++) {
					char ch = currentCmds.get(1).charAt(j);
					if(Character.isDigit(ch)) {
						numBuilder.append(ch);
					}
					else {
						unitBuilder.append(ch);
					}
				}
				long num = Integer.parseInt(numBuilder.toString());
				String unit = unitBuilder.toString();
				if(unit.equals("s")) {
					num = num * 1000;
				}
				if(num >= 0) {
					this.timeout = num;
				}
				int baseIndex = 2;
				ArrayList<String> newCmds = new ArrayList<String>();
				int size = currentCmds.size();
				for(int j = baseIndex; j < size; j++) {
					newCmds.add(currentCmds.get(j));
				}
				currentCmds = newCmds;
			}
			else if(currentCmds.get(0).equals(DShellGrammar.trace)) {
				enableTrace = checkTraceRequirements();
				int baseIndex = 1;
				ArrayList<String> newCmds = new ArrayList<String>();
				int size = currentCmds.size();
				for(int j = baseIndex; j < size; j++) {
					newCmds.add(currentCmds.get(j));
				}
				currentCmds = newCmds;
			}
			else if(currentCmds.get(0).equals(DShellGrammar.background)) {
				this.option.setFlag(background, true);
				continue;
			}
			newCmdsBuffer.add(currentCmds);
		}
		if(this.option.is(throwable)) {
			this.option.setFlag(tracable, enableTrace);
		}
		return newCmdsBuffer;
	}

	private PseudoProcess[] createProcs(ArrayList<ArrayList<String>> cmdsList) {
		ArrayList<PseudoProcess> procBuffer = new ArrayList<PseudoProcess>();
		for(ArrayList<String> currentCmds : cmdsList) {
			String cmdSymbol = currentCmds.get(0);
			PseudoProcess prevProc = null;
			int size = procBuffer.size();
			if(size > 0) {
				prevProc = procBuffer.get(size - 1);
			}
			if(cmdSymbol.equals("<")) {
				prevProc.setInputRedirect(currentCmds.get(1));
			}
			else if(cmdSymbol.equals("1>") || cmdSymbol.equals(">")) {
				prevProc.setOutputRedirect(PseudoProcess.STDOUT_FILENO, currentCmds.get(1), false);
			}	
			else if(cmdSymbol.equals("1>>") || cmdSymbol.equals(">>")) {
				prevProc.setOutputRedirect(PseudoProcess.STDOUT_FILENO, currentCmds.get(1), true);
			}
			else if(cmdSymbol.equals("2>")) {
				prevProc.setOutputRedirect(PseudoProcess.STDERR_FILENO, currentCmds.get(1), false);
			}
			else if(cmdSymbol.equals("2>>")) {
				prevProc.setOutputRedirect(PseudoProcess.STDERR_FILENO, currentCmds.get(1), true);
			}
			else if(cmdSymbol.equals("&>") || cmdSymbol.equals(">&")) {
				prevProc.setOutputRedirect(PseudoProcess.STDOUT_FILENO, currentCmds.get(1), false);
				prevProc.mergeErrorToOut();
			}
			else if(cmdSymbol.equals("&>>")) {
				prevProc.setOutputRedirect(PseudoProcess.STDOUT_FILENO, currentCmds.get(1), true);
				prevProc.mergeErrorToOut();
			}
			else {
				procBuffer.add(this.createProc(currentCmds));
			}
		}
		int size = procBuffer.size();
		procBuffer.get(0).setFirstProcFlag(true);
		procBuffer.get(size - 1).setLastProcFlag(true);
		return procBuffer.toArray(new PseudoProcess[size]);
	}

	private PseudoProcess createProc(ArrayList<String> cmds) {
		PseudoProcess proc = BuiltinCommandMap.createCommand(cmds);
		if(proc != null) {
			return proc;
		}
		proc = new SubProc(this.option);
		proc.setArgumentList(cmds);
		return proc;
	}

	// called by ModifiedJavaScriptSourceGenerator#VisitCommandNode 
	public static void ExecCommandVoidJS(ArrayList<ArrayList<String>> cmdsList) {
		TaskOption option = TaskOption.of(VoidType, printable, throwable);
		new TaskBuilder(cmdsList, option).invoke();
	}

	public static int ExecCommandIntJS(ArrayList<ArrayList<String>> cmdsList) {
		TaskOption option = TaskOption.of(IntType, printable, returnable);
		return ((Integer)new TaskBuilder(cmdsList, option).invoke()).intValue();
	}

	public static boolean ExecCommandBoolJS(ArrayList<ArrayList<String>> cmdsList) {
		TaskOption option = TaskOption.of(BooleanType, printable, returnable);
		return ((Boolean)new TaskBuilder(cmdsList, option).invoke()).booleanValue();
	}

	public static String ExecCommandStringJS(ArrayList<ArrayList<String>> cmdsList) {
		TaskOption option = TaskOption.of(StringType, returnable);
		return (String)new TaskBuilder(cmdsList, option).invoke();
	}

	public static Task ExecCommandTaskJS(ArrayList<ArrayList<String>> cmdsList) {
		TaskOption option = TaskOption.of(TaskType, printable, returnable);
		return (Task)new TaskBuilder(cmdsList, option).invoke();
	}

	// called by ModifiedJavaByteCodeGenerator#VisitCommandNode
	public static void ExecCommandVoid(String[][] cmds) {
		TaskOption option = TaskOption.of(VoidType, printable, throwable);
		new TaskBuilder(toCmdsList(cmds), option).invoke();
	}

	public static int ExecCommandInt(String[][] cmds) {
		TaskOption option = TaskOption.of(IntType, printable, returnable);
		return ((Integer)new TaskBuilder(toCmdsList(cmds), option).invoke()).intValue();
	}

	public static boolean ExecCommandBool(String[][] cmds) {
		TaskOption option = TaskOption.of(BooleanType, printable, returnable);
		return ((Boolean)new TaskBuilder(toCmdsList(cmds), option).invoke()).booleanValue();
	}

	public static String ExecCommandString(String[][] cmds) {
		TaskOption option = TaskOption.of(StringType, returnable);
		return (String)new TaskBuilder(toCmdsList(cmds), option).invoke();
	}

	public static Task ExecCommandTask(String[][] cmds) {
		TaskOption option = TaskOption.of(TaskType, printable, returnable, throwable);
		return (Task)new TaskBuilder(toCmdsList(cmds), option).invoke();
	}

	private static ArrayList<ArrayList<String>> toCmdsList(String[][] cmds) {
		ArrayList<ArrayList<String>> cmdsList = new ArrayList<ArrayList<String>>();
		for(String[] cmd : cmds) {
			ArrayList<String> cmdList = new ArrayList<String>();
			for(String tempCmd : cmd) {
				cmdList.add(tempCmd);
			}
			cmdsList.add(cmdList);
		}
		return cmdsList;
	}

	private static boolean checkTraceRequirements() {
		if(System.getProperty("os.name").equals("Linux")) {
			SubProc.traceBackendType = SubProc.traceBackend_ltrace;
			return Utils.isUnixCommand("ltrace");
		}
		System.err.println("Systemcall Trace is Not Supported");
		return false;
	}
}

abstract class PseudoProcess {
	public static final int STDOUT_FILENO = 1;
	public static final int STDERR_FILENO = 2;

	protected OutputStream stdin = null;
	protected InputStream stdout = null;
	protected InputStream stderr = null;

	protected StringBuilder cmdNameBuilder;
	protected ArrayList<String> commandList;
	protected StringBuilder sBuilder;

	protected boolean stdoutIsDirty = false;
	protected boolean stderrIsDirty = false;

	protected boolean isFirstProc = false;
	protected boolean isLastProc = false;

	protected int retValue = 0;

	public PseudoProcess() {
		this.cmdNameBuilder = new StringBuilder();
		this.commandList = new ArrayList<String>();
		this.sBuilder = new StringBuilder();
	}

	public void setArgumentList(ArrayList<String> argList) {
		this.commandList = argList;
		int size = this.commandList.size();
		for(int i = 0; i < size; i++) {
			if(i != 0) {
				this.cmdNameBuilder.append(" ");
			}
			this.cmdNameBuilder.append(this.commandList.get(i));
		}
	}
	abstract public void mergeErrorToOut();
	abstract public void setInputRedirect(String readFileName);
	abstract public void setOutputRedirect(int fd, String writeFileName, boolean append);
	abstract public void start();
	abstract public void kill();
	abstract public void waitTermination();

	public void pipe(PseudoProcess srcProc) {
		new PipeStreamHandler(srcProc.accessOutStream(), this.stdin, true).start();
	}

	public void setFirstProcFlag(boolean isFirstProc) {
		this.isFirstProc = isFirstProc;
	}

	public void setLastProcFlag(boolean isLastProc) {
		this.isLastProc = isLastProc;
	}

	public InputStream accessOutStream() {
		if(!this.stdoutIsDirty) {
			this.stdoutIsDirty = true;
			return this.stdout;
		}
		return null;
	}

	public InputStream accessErrorStream() {
		if(!this.stderrIsDirty) {
			this.stderrIsDirty = true;
			return this.stderr;
		}
		return null;
	}

	public int getRet() {
		return this.retValue;
	}

	public String getCmdName() {
		return this.cmdNameBuilder.toString();
	}

	public boolean isTraced() {
		return false;
	}

	@Override public String toString() {
		return this.sBuilder.toString();
	}
}

class SubProc extends PseudoProcess {
	public final static int traceBackend_ltrace = 0;
	public static int traceBackendType = traceBackend_ltrace;

	private final static String logdirPath = "/tmp/dshell-trace-log";
	private static int logId = 0;

	private ProcessBuilder procBuilder;
	private Process proc;
	private TaskOption taskOption;
	public boolean isKilled = false;
	public String logFilePath = null;

	private static String createLogNameHeader() {
		Calendar cal = Calendar.getInstance();
		StringBuilder logNameHeader = new StringBuilder();
		logNameHeader.append(cal.get(Calendar.YEAR) + "-");
		logNameHeader.append((cal.get(Calendar.MONTH) + 1) + "-");
		logNameHeader.append(cal.get(Calendar.DATE) + "-");
		logNameHeader.append(cal.get((Calendar.HOUR) + 1) + ":");
		logNameHeader.append(cal.get(Calendar.MINUTE) + "-");
		logNameHeader.append(cal.get(Calendar.MILLISECOND));
		logNameHeader.append("-" + logId++);
		return logNameHeader.toString();
	}

	public SubProc(TaskOption taskOption) {
		super();
		this.taskOption = taskOption;
		this.initTrace();
	}

	private void initTrace() {
		if(this.taskOption.is(tracable)) {
			logFilePath = new String(logdirPath + "/" + createLogNameHeader() + ".log");
			new File(logdirPath).mkdir();
			String[] traceCmd;
			if(traceBackendType == traceBackend_ltrace) {
				String[] backend_strace = {"ltrace", "-t", "-f", "-S", "-o", logFilePath};
				traceCmd = backend_strace;
			}
			else {
				throw new RuntimeException("invalid trace backend type");
			}
			for(int i = 0; i < traceCmd.length; i++) {
				this.commandList.add(traceCmd[i]);
			}
		}
	}

	@Override
	public void setArgumentList(ArrayList<String> argList) {
		String arg = argList.get(0);
		this.cmdNameBuilder.append(arg);
		if(arg.equals("sudo")) {
			ArrayList<String> newCommandList = new ArrayList<String>();
			newCommandList.add(arg);
			for(String cmd : this.commandList) {
				newCommandList.add(cmd);
			}
			this.commandList = newCommandList;
		}
		else {
			this.commandList.add(arg);
		}
		this.sBuilder.append("[");
		this.sBuilder.append(arg);
		int size = argList.size();
		for(int i = 1; i < size; i++) {
			arg = argList.get(i);
			this.commandList.add(arg);
			this.cmdNameBuilder.append(" " + arg);
			this.sBuilder.append(", ");
			this.sBuilder.append(arg);
		}
		this.sBuilder.append("]");
		this.procBuilder = new ProcessBuilder(this.commandList.toArray(new String[this.commandList.size()]));
		this.procBuilder.redirectError(Redirect.INHERIT);
	}

	@Override
	public void start() {
		try {
			this.setStreamBehavior();
			this.proc = procBuilder.start();
			this.stdin = this.proc.getOutputStream();
			this.stdout = this.proc.getInputStream();
			this.stderr = this.proc.getErrorStream();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void mergeErrorToOut() {
		this.procBuilder.redirectErrorStream(true);
		this.sBuilder.append("mergeErrorToOut");
	}

	private void setStreamBehavior() {
		if(this.isFirstProc) {
			if(this.procBuilder.redirectInput().file() == null) {
				procBuilder.redirectInput(Redirect.INHERIT);
			}
		}
		if(this.isLastProc) {
			if(this.procBuilder.redirectOutput().file() == null && !this.taskOption.supportStdoutHandler()) {
				procBuilder.redirectOutput(Redirect.INHERIT);
			}
		}
		if(this.procBuilder.redirectError().file() == null && this.taskOption.supportStderrHandler()) {
			this.procBuilder.redirectError(Redirect.PIPE);
		}
	}

	@Override
	public void setInputRedirect(String readFileName) {
		this.procBuilder.redirectInput(new File(readFileName));
		this.sBuilder.append(" <");
		this.sBuilder.append(readFileName);
	}

	@Override
	public void setOutputRedirect(int fd, String writeFileName, boolean append) {
		File file = new File(writeFileName);
		Redirect redirDest = Redirect.to(file);
		if(append) {
			redirDest = Redirect.appendTo(file);
		}
		if(fd == STDOUT_FILENO) {
			this.procBuilder.redirectOutput(redirDest);
		} 
		else if(fd == STDERR_FILENO) {
			this.procBuilder.redirectError(redirDest);
		}
		this.sBuilder.append(" " + fd);
		this.sBuilder.append(">");
		if(append) {
			this.sBuilder.append(">");
		}
		this.sBuilder.append(writeFileName);
	}

	@Override
	public void waitTermination() {
		try {
			this.retValue = this.proc.waitFor();
		}
		catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void kill() {
		if(System.getProperty("os.name").startsWith("Windows")) {
			this.proc.destroy();
			return;
		} 
		try {
			// get target pid
			Field pidField = this.proc.getClass().getDeclaredField("pid");
			pidField.setAccessible(true);
			int pid = pidField.getInt(this.proc);
			
			// kill process
			String[] cmds = {"kill", "-9", Integer.toString(pid)};
			Process procKiller = new ProcessBuilder(cmds).start();
			procKiller.waitFor();
			this.isKilled = true;
			//LibGreenTea.print("[killed]: " + this.getCmdName());
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void checkTermination() {
		this.retValue = this.proc.exitValue();
	}

	public String getLogFilePath() {
		return this.logFilePath;
	}

	public void deleteLogFile() {
		if(this.logFilePath != null) {
			new File(this.logFilePath).delete();
		}
	}

	@Override public boolean isTraced() {
		return this.taskOption.is(tracable);
	}
}

// copied from http://blog.art-of-coding.eu/piping-between-processes/
class PipeStreamHandler extends Thread {
	private InputStream input;
	private OutputStream[] outputs;
	private boolean closeInput;
	private boolean[] closeOutputs;

	public PipeStreamHandler(InputStream input, OutputStream output, boolean closeStream) {
		this.input = input;
		this.outputs = new OutputStream[1];
		this.outputs[0] = output;
		if(output == null) {
			this.outputs[0] = new NullStream();
		}
		this.closeInput = closeStream;
		this.closeOutputs = new boolean[1];
		this.closeOutputs[0] = closeStream;
	}

	public PipeStreamHandler(InputStream input, 
			OutputStream[] outputs, boolean closeInput, boolean[] closeOutputs) {
		this.input = input;
		this.outputs = new OutputStream[outputs.length];
		this.closeInput = closeInput;
		this.closeOutputs = closeOutputs;
		for(int i = 0; i < this.outputs.length; i++) {
			this.outputs[i] = (outputs[i] == null) ? new NullStream() : outputs[i];
		}
	}

	@Override
	public void run() {
		if(this.input == null) {
			return;
		}
		try {
			byte[] buffer = new byte[512];
			int read = 0;
			while(read > -1) {
				read = this.input.read(buffer, 0, buffer.length);
				if(read > -1) {
					for(int i = 0; i < this.outputs.length; i++) {
						this.outputs[i].write(buffer, 0, read);
					}
				}
			}
			if(this.closeInput) {
				this.input.close();
			}
			for(int i = 0; i < this.outputs.length; i++) {
				if(this.closeOutputs[i]) {
					this.outputs[i].close();
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	class NullStream extends OutputStream {
		@Override
		public void write(int b) throws IOException {	// do nothing
		}
		@Override
		public void close() {	//do nothing
		}
	}
}

