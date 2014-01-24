この章では、 関数の定義と使い方について説明します。  

# ユーザー定義関数
***
関数は次のような構文で定義されます。 

function 関数名(引数名: 引数の型, ...): 戻り値の型 { 本体 }

<pre class="nums:true toolbar:1 plain:true lang:scala highlight:0 decode:true " title="サンプル: FunctionSample1.ds" >
function f(a: int, b: int): int {
  return a + b;
}

f(1, 2);
f(3, 4);

</pre>

<pre class="toolbar:1" title="実行例">
$ dshell FunctionSample1.ds
3
7
</pre>

引数と戻り値の型は省略可能です。  

<pre class="nums:true toolbar:1 plain:true lang:scala highlight:0 decode:true " title="サンプル: FunctionSample2.ds" >
function f(a, b) {
  return a + b;
}

println(f(1, 2));
println(f(3, 4));

</pre>

<pre class="toolbar:1" title="実行例">
$ dshell FunctionSample2.ds
3
7
</pre>

* 関数はコールされる前に定義されていなければなりません。(関数のプロトタイプ宣言はできません) 
* 同じ関数名で引数の型・数が異なる場合、多重定義することが可能です。
* 同じ関数名で引数の型・数が同じ場合、後優先で関数が上書きされます。
