package dshell.exception.errno;

import dshell.exception.RelatedSyscallException;
import dshell.lib.DerivedFromErrno;
import dshell.lib.Errno;

@DerivedFromErrno(value = Errno.E2BIG)
public class TooManyArgsException extends RelatedSyscallException {
	private static final long serialVersionUID = -9207906526678278894L;

	public TooManyArgsException(String message) {
		super(message);
	}
}