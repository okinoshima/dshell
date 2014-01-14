package dshell;

import java.io.InputStream;
import java.io.OutputStream;

public class Task {
	private ProcMonitor monitor;
	private TaskBuilder dshellProc;
	private boolean terminated = false;
	private final boolean isAsyncTask;
	private MessageStreamHandler stdoutHandler;
	private MessageStreamHandler stderrHandler;
	private String stdoutMessage;
	private String stderrMessage;
	private StringBuilder sBuilder;

	public Task(TaskBuilder dshellProc) {
		this.dshellProc = dshellProc;
		// start task
		int OptionFlag = this.dshellProc.getOptionFlag();
		PseudoProcess[] Processes = this.dshellProc.getProcesses();
		int ProcessSize = Processes.length;
		int lastIndex = ProcessSize - 1;
		PseudoProcess lastProc = Processes[lastIndex];

		OutputStream stdoutStream = null;
		if(Utils.is(OptionFlag, TaskBuilder.printable)) {
			stdoutStream = System.out;
		}
		InputStream[] srcOutStreams = new InputStream[1];
		InputStream[] srcErrorStreams = new InputStream[ProcessSize];

		Processes[0].start();
		for(int i = 1; i < ProcessSize; i++) {
			Processes[i].start();
			Processes[i].pipe(Processes[i - 1]);
		}

		// Start Message Handler
		// stdout
		srcOutStreams[0] = lastProc.accessOutStream();
		this.stdoutHandler = new MessageStreamHandler(srcOutStreams, stdoutStream);
		this.stdoutHandler.showMessage();
		// stderr
		for(int i = 0; i < ProcessSize; i++) {
			srcErrorStreams[i] = Processes[i].accessErrorStream();
		}
		this.stderrHandler = new MessageStreamHandler(srcErrorStreams, System.err);
		this.stderrHandler.showMessage();
		// start monitor
		this.isAsyncTask = Utils.is(this.dshellProc.getOptionFlag(), TaskBuilder.background);
		this.sBuilder = new StringBuilder();
		if(this.isAsyncTask) {
			this.sBuilder.append("#AsyncTask");
		}
		else {
			this.sBuilder.append("#SyncTask");
		}
		this.sBuilder.append("\n");
		this.sBuilder.append(this.dshellProc.toString());
		this.monitor = new ProcMonitor(this, this.dshellProc, isAsyncTask);
		this.monitor.start();
	}

	@Override public String toString() {
		return this.sBuilder.toString();
	}

	public void join() {
		if(this.terminated) {
			return;
		}
		try {
			this.terminated = true;
			this.stdoutMessage = this.stdoutHandler.waitTermination();
			this.stderrMessage = this.stderrHandler.waitTermination();
			monitor.join();
		} 
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		//new ShellExceptionRaiser(this.dshellProc).raiseException();
	}

	public void join(long timeout) {
		
	}

	public String getOutMessage() {
		this.checkTermination();
		return this.stdoutMessage;
	}

	public String getErrorMessage() {
		this.checkTermination();
		return this.stderrMessage;
	}

	public int getExitStatus() {
		this.checkTermination();
		PseudoProcess[] procs = this.dshellProc.getProcesses();
		return procs[procs.length - 1].getRet();
	}

	private void checkTermination() {
		if(!this.terminated) {
			throw new IllegalThreadStateException("Task is not Terminated");
		}
	}
}

class ProcMonitor extends Thread {	// TODO: support exit handle
	private Task task;
	private TaskBuilder dshellProc;
	private final boolean isBackground;
	public final long timeout;

	public ProcMonitor(Task task, TaskBuilder dShellProc, boolean isBackground) {
		this.task = task;
		this.dshellProc = dShellProc;
		this.isBackground = isBackground;
		this.timeout =  this.dshellProc.getTimeout();
	}

	@Override public void run() {
		PseudoProcess[] processes = this.dshellProc.getProcesses();
		int size = processes.length;
		if(this.timeout > 0) { // timeout
			try {
				Thread.sleep(timeout);	// ms
				StringBuilder msgBuilder = new StringBuilder();
				msgBuilder.append("Timeout Task: ");
				for(int i = 0; i < size; i++) {
					processes[i].kill();
					if(i != 0) {
						msgBuilder.append("| ");
					}
					msgBuilder.append(processes[i].getCmdName());
				}
				System.err.println(msgBuilder.toString());
				// run exit handler
			} 
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			return;
		}
		if(!this.isBackground) {	// wait termination for sync task
			for(int i = 0; i < size; i++) {
				processes[i].waitTermination();
			}
		}
		while(this.isBackground) {	// check termination for async task
			int count = 0;
			for(int i = 0; i < size; i++) {
				SubProc subProc = (SubProc)processes[i];
				try {
					subProc.checkTermination();
					count++;
				}
				catch(IllegalThreadStateException e) {
					// process has not terminated yet. do nothing
				}
			}
			if(count == size) {
				StringBuilder msgBuilder = new StringBuilder();
				msgBuilder.append("Terminated Task: ");
				for(int i = 0; i < size; i++) {
					if(i != 0) {
						msgBuilder.append("| ");
					}
					msgBuilder.append(processes[i].getCmdName());
				}
				System.err.println(msgBuilder.toString());
				// run exit handler
				return;
			}
			try {
				Thread.sleep(100); // sleep thread
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
}