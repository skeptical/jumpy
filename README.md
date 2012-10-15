<H3 style="color:black">PMS-<span style="color:#ff6600">Jumpy</span> - Jump into Python (and onto XBMC)</H3>

PMS-Jumpy (<span class="orange">J</span>ava <span class="orange">U</span>nderlay for <span class="orange">M</span>odules in <span class="orange">PY</span>thon) is a framework for scripts or other external processes to plug into <a href="http://www.ps3mediaserver.org/" target="_blank">PS3 Media Server</a> (or its cousin <a href="http://www.universalmediaserver.com/" target="_blank">UMS</a>) and mainly
<ul>
<li>add folders and media items to the xmb.</li>
<li>act as user-defined external players.</li>
</ul>

The API is accessible from python via jumpy's python module, or from other languages via system calls. Interaction is through a localhost socket managed by <a href="http://py4j.sourceforge.net" target="_blank">py4j</a>.

Jumpy began with my having grown tired of occasionally poaching stuff 'by hand' from <a href="http://xbmc.org" target="_blank">xbmc</a> addons for use in PMS, and deciding to see if I could mock the xbmc plugin API itself so as to run these addons under PMS as-is. The result is Jumpy plus <a href="https://github.com/skeptical/jumpy-xbmc">jumpy-xbmc</a>, an included set of python modules to 'jump' the xbmc addons.

Jumpy-xbmc works with most xbmc video addons: <i>Hulu, Free Cable, Al Jazeera, PBS, Academic Earth, YouTube</i>, and many more. Addons are 'mostly' functional in the sense that all content is shown, but interactive features that require user input are inactive.

Jumpy itself is general-purpose, of course, and you can use it to plug <a href="http://skeptical.github.com/jumpy/scripts.html" target="_blank">your own scripts/commands</a>, python or other, into PMS as well.

<b style="color:black">Links</b>
<ul>
<li><a href="http://skeptical.github.com/jumpy/readme.html" target="_blank">Documentation</a></li>
<li><a href="http://www.universalmediaserver.com/forum/viewtopic.php?f=6&t=288" target="_blank">Forum</a></li>
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

