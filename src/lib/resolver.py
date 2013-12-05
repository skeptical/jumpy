################# configuration: use True/False #################
enable_xbmc = True
enable_youtube_dl = True
enable_get_flash_videos = False
#################################################################

import os, sys, traceback, __builtin__
from cStringIO import StringIO
from subprocess import Popen, PIPE
pms_await = True
import jumpy


class resolver:

	def __init__(self):
		self.url = self.name = self.thumb = self.sub = None

	def resolve(self, url):
		self.url = None
		for scraper in scrapers:
			try:
				scraper.resolve(url, self)
			except: traceback.print_exc()
			if self.url:
				pms.log('%s : %s' % (scraper.name, self.url), True)
				return self.url
		return None

	def add(self, url, name=None, thumb=None, sub=None):
		self.url = url.strip()
		self.name = name.strip() if name else None
		self.thumb = thumb.strip() if thumb else None
		self.sub = sub.strip() if sub else None


class cbresolver(resolver):

	class Java:
		implements = ['net.pms.external.infidel.jumpy.resolver$Resolver']

	def __init__(self):
		resolver.__init__(self)
		try:
			pms.register(self)
		except: traceback.print_exc()

# scrapers

class _xbmc:

	name = 'xbmc-urlresolver'

	def __init__(self, enable):
		lib = os.path.join(home, 'xbmc')
		if os.path.exists(lib):
			if lib in sys.path:
				sys.path.remove(lib)
			sys.path.insert(1, lib)
		try:
			import xbmcinit
			__builtin__.xbmcinit = xbmcinit
			addondir = os.path.join(_special['home'], 'addons', 'script.module.urlresolver')
			id = xbmcinit.read_addon(dir=addondir, full=True)
			scrapers.append(self)
			sys.stderr.write('%s version %s\n' % (_info[id]['name'], _info[id]['version']))
			if enable:
				sys.path.extend([p for p in _info[id]['_pythonpath'] if p not in sys.path])
				argv = sys.argv
				sys.argv = [sys.argv[0], '?']
				import urlresolver
				__builtin__.urlresolver = urlresolver
				sys.argv = argv
		except:
			if self in scrapers: scrapers.remove(self)
			sys.stderr.write('unable to open xbmc-urlresolver\n')
			traceback.print_exc()

	def resolve(self, url, resolver):
		u = None
		if 'plugin://' in url:
			if url.startswith('plugin://'):
				u = url
			elif url.startswith('['):
				argv = url[1:-1].split(' , ')
				u = (argv[1] if len(argv) > 1 and argv[1].startswith('plugin://') else None)
		else:
			u = urlresolver.resolve(url)
		if u:
			if 'plugin://' in u:
				pmsaddItem = __builtin__.pms.addItem
				pmsutil = __builtin__.pms.util
				def addItem(itemtype, name, argv, thumb=None, data=None):
					if type(argv).__name__ == 'list':
						argv = jumpy.flatten(argv)
					resolver.add(argv, name, thumb)
				__builtin__.pms.addItem = addItem
				def util(action, arg1=None, arg2=None):
					if action == PMS_SUBTITLE:
						resolver.sub = arg1.strip()
						return ''
					else:
						return pmsutil(action, arg1, arg2)
				__builtin__.pms.util = util
				xbmcinit.run_addon(u)
				__builtin__.pms.addItem = pmsaddItem
				__builtin__.pms.util = pmsutil
			else:
				resolver.add(u)


class _youtube_dl:

	name = 'youtube-dl'

	def __init__(self, enable):
		lib = pms.getProperty('youtube-dl.lib.path')
		if not lib:
			lib = pms.getProperty('youtube-dl.path')
		if lib:
			lib = os.path.join( \
				os.path.dirname(os.path.dirname(os.path.dirname(home))), \
				os.path.dirname(lib))
			if os.path.exists(os.path.join(lib, 'youtube_dl')):
				if lib in sys.path:
					sys.path.remove(lib)
				sys.path.insert(1, lib)
		try:
			import youtube_dl
			__builtin__.youtube_dl = youtube_dl
			sys.stderr.write('youtube-dl version %s\n' % youtube_dl.__version__)
			scrapers.append(self)
			if enable:
				# omit the last extractor (GenericIE, which takes forever)
				extractors = youtube_dl.gen_extractors()[:-1]
				youtube_dl.gen_extractors = lambda:extractors
				if '_ALL_CLASSES' in youtube_dl.extractor.__dict__:
					youtube_dl.extractor._ALL_CLASSES = youtube_dl.extractor._ALL_CLASSES[:-1]
				# set up redirection
				self.devnull = open(os.devnull, 'w')
				self.noexit = lambda m:None
		except:
			if self in scrapers: scrapers.remove(self)
			sys.stderr.write('unable to open youtube-dl\n')

	def resolve(self, url, resolver):
		if 'plugin://' in url: return
		stdout, stderr, exit = sys.stdout, sys.stderr, sys.exit
		# prevent youtube_dl from shutting us down, discard stderr, capture stdout
		sys.exit = self.noexit
		sys.stderr = self.devnull
		sys.stdout = capture = StringIO()
		# TODO: --cookies (https://github.com/rg3/youtube-dl/issues/41)
		#       --max-downloads (overuse triggers '402: Payment Required')
		youtube_dl.main(['--get-title', '-g', '--get-thumbnail', url])
		sys.stdout, sys.stderr, sys.exit = stdout, stderr, exit
		o = capture.getvalue()
		if o:
			# note: output order is always title, url, thumb
			o = o.splitlines()[:3]
			resolver.add(o[1], o[0], o[2])


class _get_flash_videos:

	name = 'get-flash-videos'

	def __init__(self, enable):
		self.gfv = pms.getProperty('get-flash-videos.path')
		if os.path.exists(self.gfv):
			scrapers.append(self)
		else:
			sys.stderr.write('unable to open get-flash-videos\n')

	def resolve(self, url, resolver):
		p = Popen([self.gfv, '-i', '-y', '-r', 'high', url], stdout=PIPE, stderr=PIPE)
		o = p.communicate()[0]
		if p.returncode == 0 and o:
			print o
			info = {}
			for line in o.splitlines()[1:]:
				k,v = line.split(': ', 1)
				info[k] = v
			resolver.add(info['Content-Location'], info['Title'])


scrapers = []
home = pms.getHome()
enable = (False if 'validate' in sys.argv else True)

if enable_youtube_dl:
	_youtube_dl(enable)
if enable_xbmc:
	_xbmc(enable)
if enable_get_flash_videos:
	_get_flash_videos(enable)

print 'scrapers: %s' % ' '.join([s.name for s in scrapers])

if __name__ == "__main__":
	if 'start' in sys.argv:
		cbresolver()
	elif 'test' in sys.argv:
		resolver().resolve(sys.argv[2])
	elif len(scrapers):
		pms.setEnv('resolvers', ' '.join([s.name for s in scrapers]))

