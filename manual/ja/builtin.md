この章では、 D-Shell のビルトインコマンドについて説明します。  

# cd
***
カレントディレクトリを変更します。  

使用方法: cd [ディレクトリパス]  

* ディレクトリパス  
移動したいディレクトリパスを指定します。  
省略した場合、ホームディレクトリに移動します。  

<pre class="toolbar:0 highlight:0">
> pwd
hoge:/home/hogehoge/working/dshell
> cd
> pwd
hoge:/home/hogehoge
</pre>

# exit
***
現在のシェルを終了します。  

使用方法: exit [終了コード]  

* 終了コード  
シェル終了時の終了コードを指定します。  
省略した場合、終了コード0を戻り値としてシェルを終了します。  

<pre class="toolbar:0 highlight:0">
> exit
$ echo $?
0

> exit 1
$ echo $?
1
</pre>

# log
***
使用方法: log [文字列]  

* 文字列  
文字列をログに出力します。  
ログの出力先は、シェル起動時のオプションで指定します。  

<pre class="nums:true toolbar:1 lang:scala decode:true" title="サンプル: Logger.ds" >
function func() {
  log "logging test"
}
func()
</pre>

<pre class="toolbar:1 highlight:0" title="実行例">
$ dshell --logging:./test.log Logger.ds
logging test
$ cat ./test.log
Feb  3 15:39:27 WARN - logging test

$ dshell --logging:stdout Logger.ds
logging test

$ dshell --logging:stderr Logger.ds
logging test

$ dshell --logging:syslog Logger.ds
logging test
$ tail -1 /var/log/syslog
Feb  3 15:39:27 WARN - logging test
</pre>

# assert
***
与えられた論理式が偽の場合、メッセージを表示してシェルを終了します。  
assert はデバッグ目的にのみ使用するべきです。  

使用方法: assert 論理式  

* 論理式  
与えられた論理式が偽の場合、メッセージを出力し、終了コード1を戻り値としてシェルを終了します。  

<pre class="nums:true toolbar:1 lang:scala decode:true" title="サンプル: Assert.ds" >
function func() {
  var num = 5

  assert(num == 5)
  log "assert check 1"

  assert(num instanceof int)
  log "assert check 2"

  assert(num > 2)
  log "assert check 3"
}
func()
</pre>

<pre class="toolbar:1 highlight:0" title="実行例">
$ dshell Assert.ds
assert check 1
assert check 2
Assertion Faild
$ echo $?
1
</pre>
