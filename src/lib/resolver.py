import os, sys, traceback, __builtin__
from cStringIO import StringIO
from subprocess import Popen, PIPE
pms_await = True
import jumpy
from pageScanner import scanner

version = '1.3'

class resolver:

	def __init__(self, s=None):
		init(s)

		self.url = self.name = self.thumb = self.sub = None

	def resolve(self, url):
		return self._resolve(url)

	def _resolve(self, url):
		self.url = None
		for scraper in scrapers:
			try:
				scraper.resolve(url, self)
			except: traceback.print_exc()
			if self.url:
				pms.log('%s : %s' % (scraper.name(), self.url), True)
				return self.url
		return None

	def add(self, url, name=None, thumb=None, sub=None):
		self.url = url.strip()
		self.name = name.strip() if name else None
		self.thumb = thumb.strip() if thumb else None
		self.sub = sub.strip() if sub else None


class cbresolver(scanner):

	class Java:
		implements = ['net.pms.external.infidel.jumpy.resolver$Resolver']

	def resolve(self, url):
		if 'plugin://' in url or url.startswith('['):
			return self.resolver.resolve(url)
		return self.scan(url)

	def __init__(self, s=None):
		scanner.__init__(self, resolver(s), scanner.SINGLE)
		try:
			pms.register(self)
		except: traceback.print_exc()

# scrapers

class _scraper:
	def name(self):
		return self.__class__.__name__[1:].replace('_', '-')

class _xbmc_urlresolver(_scraper):

	def __init__(self):
		self.started = False
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
			sys.path.extend([p for p in _info[id]['_pythonpath'] if p not in sys.path])
			sys.stderr.write('%s version %s\n' % (_info[id]['name'], _info[id]['version']))
			scrapers.append(self)
		except:
			if self in scrapers: scrapers.remove(self)
			sys.stderr.write('unable to open xbmc-urlresolver\n')
			traceback.print_exc()

	def start(self):
		try:
			argv = sys.argv
			sys.argv = [sys.argv[0], '?']
			import urlresolver
			__builtin__.urlresolver = urlresolver
			sys.argv = argv
			self.started = True
		except:
			if self in scrapers: scrapers.remove(self)
			sys.stderr.write('unable to start xbmc-urlresolver\n')
			traceback.print_exc()

	def resolve(self, url, resolver):
		if not self.started: self.start()
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
				def addItem(itemtype, name, argv, thumb=None, mediainfo=None, data=None):
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


class _youtube_dl(_scraper):

	def __init__(self):
		self.started = False
		self.info_dicts = []
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
		except:
			if self in scrapers: scrapers.remove(self)
			sys.stderr.write('unable to open youtube-dl\n')

	def start(self):
		try:
			# omit the last extractor (GenericIE, which takes forever)
			extractors = youtube_dl.gen_extractors()[:-1]
			youtube_dl.gen_extractors = lambda:extractors
			if '_ALL_CLASSES' in youtube_dl.extractor.__dict__:
				youtube_dl.extractor._ALL_CLASSES = youtube_dl.extractor._ALL_CLASSES[:-1]
			# set up redirection
			self.devnull = open(os.devnull, 'w')
			self.noexit = lambda m:None
			ydl_prepare_filename = youtube_dl.YoutubeDL.prepare_filename
			this = self
			def prepare_filename(self, info_dict):
				filename = ydl_prepare_filename(self, info_dict)
				info_dict['_filename'] = filename
				this.info_dicts.append(info_dict)
				return filename
			youtube_dl.YoutubeDL.prepare_filename = prepare_filename
			self.started = True
		except:
			if self in scrapers: scrapers.remove(self)
			sys.stderr.write('unable to start youtube-dl\n')
			traceback.print_exc()

	def resolve(self, url, resolver):
		if 'plugin://' in url: return
		if not self.started: self.start()
		self.info_dicts = []
		stdout, stderr, exit = sys.stdout, sys.stderr, sys.exit
		# prevent youtube_dl from shutting us down, discard stderr/stdout
		sys.exit = self.noexit
		sys.stderr = sys.stdout = self.devnull
		# TODO: --cookies (https://github.com/rg3/youtube-dl/issues/41)
		#       --max-downloads (overuse triggers '402: Payment Required')
		youtube_dl.main(['-s', url])
		sys.stdout, sys.stderr, sys.exit = stdout, stderr, exit
		u = '\n'.join([(i['url'] + i.get('play_path', '')) for i in self.info_dicts])
		if u:
			i = self.info_dicts[0]
			resolver.add(u, i.get('fulltitle'), i.get('thumbnail'))


class _get_flash_videos(_scraper):

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

def init(resolvers=None):
	if not resolvers:
		resolvers = pms.getVar('_resolvers')
	for s in resolvers.split(' '):
		try: exec '_%s()' %(s.replace('-', '_'))
		except: traceback.print_exc()


scrapers = []
home = pms.getHome()


if __name__ == "__main__":
	if 'start' in sys.argv:
		cbresolver()
	elif 'test' in sys.argv:
		resolver('youtube-dl xbmc-urlresolver').resolve(sys.argv[2])
	elif 'validate' in sys.argv:
		init('youtube-dl xbmc-urlresolver')
		info = {
			'_title'   : 'Resolver',
			'_desc'    : 'Resolves media links using external scrapers.\n\nThis is a background sevice for WEB.conf as well as jumpy links. You can enable/disable or set the priority of each scraper below.',
			'_link'    : '[Info](http://skeptical.github.io/jumpy/readme.html#RESOLVER)',
			'_icon'    : '#question+f=#a6a8a4',
			'_version' : str(version)
		}
		info.update({k.name():[str(scrapers.index(k)+1), 'priority (1=first|2=second|-1=disable)'] for k in scrapers})
		settings = pms.setInfo(info, '.resolver')
#		sys.stderr.write('settings: %s'%settings)
		s = {k:v for k,v in settings.items() if v.isdigit()}
		pms.setVar('_resolvers', ' '.join(sorted(s, key=s.get)))

