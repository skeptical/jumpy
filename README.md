<H3 style="color:black">PMS-<span style="color:#ff6600">Jumpy</span> - Jump into Python (and onto XBMC)</H3>

PMS-Jumpy (<span style="color:#ff6600">J</span>ava <span style="color:#ff6600">U</span>nderlay for <span style="color:#ff6600">M</span>odules in <span style="color:#ff6600">PY</span>thon) is a framework for scripts or other external processes to plug into <a href="http://www.ps3mediaserver.org/" target="_blank">PS3 Media Server</a> (or its cousin <a href="http://www.universalmediaserver.com/" target="_blank">UMS</a>), primarily to add folders and media items to the xmb.

The API is accessible from python via jumpy's python module, or from other languages via system calls. Interaction is through a localhost socket managed by <a href="http://py4j.sourceforge.net" target="_blank">py4j</a>.

Jumpy began with my having grown tired of occasionally poaching stuff 'by hand' from <a href="http://xbmc.org" target="_blank">xbmc</a> addons for use in PMS, and deciding to see if I could mock the xbmc plugin API itself so as to run these addons under PMS as-is. The result is Jumpy plus <a href="https://github.com/skeptical/jumpy-xbmc">jumpy-xbmc</a>, an included set of python modules to 'jump' the xbmc addons.

Jumpy-xbmc works with most xbmc video addons: <i>Hulu, Free Cable, Al Jazeera, PBS, Academic Earth, YouTube</i>, and many more. Addons are 'mostly' functional in the sense that all content is shown, but interactive features that require user input are inactive.

Jumpy itself is general-purpose, of course, and you can use it to plug your own scripts, python or other, into PMS as well.

<b style="color:black">Requirements</b>
<ol>
<li><a href="http://www.python.org/download/releases/2.7.3/#download" target="_blank">Python 2.7</a>.
To make life simpler <a href="http://superuser.com/questions/143119/how-to-add-python-to-the-windows-path/143121#143121" target="_blank">add python to your system path</a>
and then set up <a href="http://pypi.python.org/pypi/setuptools#installation-instructions" target="_blank">easy_install</a>.
</li>
<li><a href="http://py4j.sourceforge.net/download.html" target="_blank">py4j</a>.
</li>
<li><a href="https://github.com/chocolateboy/pmsencoder" target="_blank">PMSEncoder</a> and
<a href="http://rtmpdump.mplayerhq.hu/" target="_blank">rtmpdump</a>, not required but indispensible as always.
</li>
</ol>

<b style="color:black">XBMC Installation</b> ("optional")
<ol>
<li>Install <a href="http://xbmc.org/download/" target="_blank">xbmc</a> using the default configuration
for your platform.
</li>
<li><a href="http://wiki.xbmc.org/index.php?title=Unofficial_Add-on_Repositories" target="_blank">Install and configure</a>
some <a href="http://wiki.xbmc.org/index.php?title=Category:All_add-ons" target="_blank">addons</a>
inside xbmc (and of course test them too, some are broken). You may need to configure username/password depending
on the addon, and so on.
</li>
</ol>

<b style="color:black">Plugin Installation</b>
<ol>
<li>Unzip <i><a href="https://github.com/downloads/skeptical/jumpy/pms-jumpy-0.2.1.zip">pms-jumpy-0.2.1.zip</a></i> to your pms <i>plugins</i> folder.
</li>
<li>Open up <i>PMS.conf</i> and set <code>python.path</code>, e.g.
<pre>python.path = c:\\Python27\\python</pre>
If you're using rtmpdump, or want to enable a script language, let's say perl, create
a <code>.path</code> for each if it doesn't already exist:
<pre>
rtmpdump.path = c:\\rtmpdump\\rtmpdump.exe
perl.path = c:\\perl\\bin\\perl.exe
</pre>
</li>
<li>Restart PMS. You should see a 'Jumpy' folder on the xmb and any media addons found in your xbmc user directory.
</li>
</ol>
<b style="color:black">Links</b>
<ul>
<li><a href="http://skeptical.github.com/jumpy/readme.html" target="_blank">Documentation</a></li>
<li><a href="http://www.ps3mediaserver.org/forum/viewtopic.php?f=12&t=12518" target="_blank">Forum</a></li>
</ul>

<b style="color:black">Building</b>
<ul>
Place or symlink the following dependencies in 'lib' before running ant:
<ul>
<li><i>pms.jar</i> (<a href="http://www.ps3mediaserver.org/" target="_blank">ps3mediaserver.org</a>)</li>
<li><i>py4j0.7.jar</i> (<a href="http://py4j.sourceforge.net" target="_blank">py4j.sourceforge.net</a>)</li>
<li><i>ini4j-0.5.2.jar</i> (<a href="http://ini4j.sourceforge.net" target="_blank">ini4j.sourceforge.net</a>)</li>
<li><i>commons-exec-1.1.jar</i> (<a href="http://commons.apache.org/exec/" target="_blank">commons.apache.org/exec</a>)</li>
</ul>
</ul>

<!--
<a name=".+?">(.*)</a>
<span class="orange">  -> <span style="color:#ff6600">
<br/>  -> \n
-->
