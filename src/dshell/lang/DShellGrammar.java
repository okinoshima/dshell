package dshell.lang;

import java.util.ArrayList;

import libzen.grammar.ComparatorPattern;
import libzen.grammar.UnaryPattern;
import zen.ast.ZBlockNode;
import zen.ast.ZStringNode;
import zen.lang.ZenPrecedence;
import zen.parser.ZNameSpace;
import zen.parser.ZToken;
import zen.parser.ZTokenContext;
import dshell.grammar.ArgumentPattern;
import dshell.grammar.CommandPattern;
import dshell.grammar.DShellCatchPattern;
import dshell.grammar.DShellImportPattern;
import dshell.grammar.DShellPattern;
import dshell.grammar.DShellTryPattern;
import dshell.grammar.EnvPattern;
import dshell.grammar.ForeachPattern;
import dshell.grammar.LocationDefinePattern;
import dshell.grammar.PrefixOptionPattern;
import dshell.grammar.RedirectPattern;
import dshell.grammar.ShellStyleCommentToken;
import dshell.grammar.SuffixOptionPattern;
import dshell.lib.BuiltinCommand;

public class DShellGrammar {
	// suffix option symbol
	public final static String background = "&";
	// prefix option symbol 
	public final static String timeout = "timeout";
	public final static String trace = "trace";
	public final static String location = "location";

	public static String CommandSymbol(String Symbol) {
		return "__$" + Symbol;
	}

	public static boolean MatchStopToken(ZTokenContext TokenContext) { // ;,)]}&&||
		ZToken Token = TokenContext.GetToken();
		if(!TokenContext.HasNext()) {
			return true;
		}
		if(Token.IsIndent() || Token.EqualsText(";")) {
			return true;
		}
		if(Token.EqualsText(",") || Token.EqualsText(")") || Token.EqualsText("]") || 
				Token.EqualsText("}") || Token.EqualsText("&&") || Token.EqualsText("||")) {
			return true;
		}
		return false;
	}

	public static void ImportGrammar(ZNameSpace NameSpace, Class<?> Grammar) {
		CommandPattern commandPattern = new CommandPattern();
		DShellPattern dshellPattern = new DShellPattern();
		ComparatorPattern comparatorPattern = new ComparatorPattern();
		UnaryPattern unaryPattern = new UnaryPattern();
		PrefixOptionPattern prefixOptionPattern = new PrefixOptionPattern();

		NameSpace.AppendTokenFunc("#", new ShellStyleCommentToken());

		NameSpace.DefineStatement("import", new DShellImportPattern());
		NameSpace.DefineExpression("$Env$", new EnvPattern());
		NameSpace.DefineStatement("command", commandPattern);
		NameSpace.DefineExpression("$Command$", commandPattern);
		NameSpace.DefineExpression("$CommandArg$", new ArgumentPattern());
		NameSpace.DefineExpression("$Redirect$", new RedirectPattern());
		NameSpace.DefineExpression("$SuffixOption$", new SuffixOptionPattern());
		NameSpace.DefineExpression("$DShell$", dshellPattern);
		NameSpace.DefineRightExpression("=~", ZenPrecedence.CStyleEquals, comparatorPattern);
		NameSpace.DefineRightExpression("!~", ZenPrecedence.CStyleEquals, comparatorPattern);
		NameSpace.DefineExpression("log", unaryPattern);
		NameSpace.DefineStatement("try", new DShellTryPattern());
		NameSpace.DefineExpression("$Catch$", new DShellCatchPattern());
		NameSpace.DefineStatement(location, new LocationDefinePattern());
		NameSpace.DefineExpression(timeout, prefixOptionPattern);
		NameSpace.DefineExpression(trace, prefixOptionPattern);
		NameSpace.DefineExpression("$PrefixOption$", prefixOptionPattern);
		NameSpace.DefineStatement("for", new ForeachPattern());

		// from BultinCommandMap
		ArrayList<String> symbolList = BuiltinCommand.getCommandSymbolList();
		for(String symbol : symbolList) {
			setOptionalSymbol(NameSpace, symbol, dshellPattern);
		}
		NameSpace.Generator.AppendGrammarInfo("dshell0.1");
	}

	private static void setOptionalSymbol(ZNameSpace NameSpace, String symbol, DShellPattern dShellPattern) {
		NameSpace.DefineExpression(symbol, dShellPattern);
		NameSpace.SetGlobalSymbol(CommandSymbol(symbol), new ZStringNode(new ZBlockNode(NameSpace), null, symbol));
	}
}
