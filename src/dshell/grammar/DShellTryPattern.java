package dshell.grammar;

import dshell.ast.DShellTryNode;
import zen.ast.ZErrorNode;
import zen.ast.ZNode;
import zen.deps.ZMatchFunction;
import zen.parser.ZTokenContext;

public class DShellTryPattern extends ZMatchFunction {
	@Override
	public ZNode Invoke(ZNode ParentNode, ZTokenContext TokenContext, ZNode LeftNode) {
		ZNode TryNode = new DShellTryNode(ParentNode);
		TryNode = TokenContext.MatchToken(TryNode, "try", ZTokenContext._Required);
		TryNode = TokenContext.MatchPattern(TryNode, DShellTryNode._Try, "$Block$", ZTokenContext._Required);
		boolean foundCatchBlock = false;
		while(true) {
			if(TokenContext.IsNewLineToken("catch")) {
				TryNode = TokenContext.MatchPattern(TryNode, ZNode._AppendIndex, "$Catch$", ZTokenContext._Required);
				foundCatchBlock = true;
				continue;
			}
			if(TokenContext.MatchNewLineToken("finally")) {
				TryNode = TokenContext.MatchPattern(TryNode, DShellTryNode._Finally, "$Block$", ZTokenContext._Required);
			}
			break;
		}
		if(!foundCatchBlock) {
			return new ZErrorNode(TryNode, "not found catch block");
		}
		return TryNode;
	}

}
