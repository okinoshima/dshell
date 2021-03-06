package dshell.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public abstract class PseudoProcess {
	public static final int STDOUT_FILENO = 1;
	public static final int STDERR_FILENO = 2;

	protected OutputStream stdin = null;
	protected InputStream stdout = null;
	protected InputStream stderr = null;

	protected StringBuilder cmdNameBuilder;
	protected ArrayList<String> commandList;
	protected StringBuilder sBuilder;

	protected boolean stdinIsDirty = false;
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
	abstract public boolean checkTermination();

	public void pipe(PseudoProcess srcProc) {
		new PipeStreamHandler(srcProc.accessOutStream(), this.accessInStream(), true).start();
	}

	public void setFirstProcFlag(boolean isFirstProc) {
		this.isFirstProc = isFirstProc;
	}

	public void setLastProcFlag(boolean isLastProc) {
		this.isLastProc = isLastProc;
	}

	public OutputStream accessInStream() {
		if(!this.stdinIsDirty) {
			this.stdinIsDirty = true;
			return this.stdin;
		}
		return new PipeStreamHandler.NullOutputStream();
	}

	public InputStream accessOutStream() {
		if(!this.stdoutIsDirty) {
			this.stdoutIsDirty = true;
			return this.stdout;
		}
		return new PipeStreamHandler.NullInputStream();
	}

	public InputStream accessErrorStream() {
		if(!this.stderrIsDirty) {
			this.stderrIsDirty = true;
			return this.stderr;
		}
		return new PipeStreamHandler.NullInputStream();
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

//copied from http://blog.art-of-coding.eu/piping-between-processes/
class PipeStreamHandler extends Thread {
	public final static int defaultBufferSize = 512;
	private final InputStream input;
	private final OutputStream[] outputs;
	private final boolean closeInput;
	private final boolean[] closeOutputs;

	public PipeStreamHandler(InputStream input, OutputStream output, boolean closeStream) {
		this(input, new OutputStream[] {output}, closeStream, new boolean[]{closeStream});
	}

	public PipeStreamHandler(InputStream input, OutputStream[] outputs, boolean closeInput, boolean[] closeOutputs) {
		this.input = (input == null) ? new NullInputStream() : input;
		this.outputs = new OutputStream[outputs.length];
		this.closeInput = closeInput;
		this.closeOutputs = closeOutputs;
		for(int i = 0; i < this.outputs.length; i++) {
			this.outputs[i] = (outputs[i] == null) ? new NullOutputStream() : outputs[i];
		}
	}

	@Override
	public void run() {
		try {
			byte[] buffer = new byte[defaultBufferSize];
			int read = 0;
			while(read > -1) {
				read = this.input.read(buffer, 0, buffer.length);
				if(read > -1) {
					for(OutputStream output : this.outputs) {
						output.write(buffer, 0, read);
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
			e.printStackTrace();
			Utils.fatal(1, "IO problem");
		}
	}

	public static class NullInputStream extends InputStream {
		@Override
		public int read() throws IOException {
			return -1;
		}
		@Override
		public void close() {	//do nothing
		}
	}

	public static class NullOutputStream extends OutputStream {
		@Override
		public void write(int b) throws IOException {	// do nothing
		}
		@Override
		public void close() {	//do nothing
		}
	}
}