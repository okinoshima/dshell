package dshell.exception.errno;

import dshell.exception.RelatedSyscallException;
import dshell.lib.DerivedFromErrno;
import dshell.lib.Errno;

@DerivedFromErrno(value = Errno.EBADMSG)
public class BadMessageException extends RelatedSyscallException {
	private static final long serialVersionUID = -844920617505654673L;

	public BadMessageException(String message) {
		super(message);
	}
}