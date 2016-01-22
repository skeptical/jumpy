import os, sys, traceback, __builtin__
from cStringIO import StringIO
from subprocess import Popen, PIPE
pms_await = True
import jumpy
from pageScanner import scanner
from py4j.java_collections import MapConverter

version = '1.5'

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
				return {'uri': pms.decode(self.url), 'name': pms.decode(self.name), 'thumb': pms.decode(self.thumb), 'sub': pms.decode(self.sub),
					'details': pms.stringify(self.details) if self.details else None}
		return None

	def add(self, url, name=None, thumb=None, sub=None, details=None):
		self.url = url.strip()
		self.name = name.strip() if name else None
		self.thumb = thumb.strip() if thumb else None
		self.sub = sub.strip() if sub else None
		self.details = details if details else None


class cbresolver(scanner):

	class Java:
		implements = ['net.pms.external.infidel.jumpy.resolver$Resolver']

	def resolve(self, url):
		if 'plugin://' in url or url.startswith('['):
			r = self.resolver.resolve(url)
		else:
			r = self.scan(url)
		return MapConverter().convert(r, pms.gateway_client)# if r else None

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

	def name(self):
		return 'xbmc%s' % ('-urlresolver' if self.urlresolver else '')

	def __init__(self, with_urlresolver=True):
		self.started = False
		self.urlresolver = False
		lib = os.path.join(home, 'xbmc')
		if os.path.exists(lib):
			if lib in sys.path:
				sys.path.remove(lib)
			sys.path.insert(1, lib)
		try:
			import xbmcinit, xbmc
			__builtin__.xbmcinit = xbmcinit
			__builtin__.xbmc = xbmc
			scrapers.append(self)
		except:
			if self in scrapers: scrapers.remove(self)
			sys.stderr.write('unable to open xbmc\n')
			traceback.print_exc()
		if with_urlresolver:
			try:
				addondir = os.path.join(_special['home'], 'addons', 'script.module.urlresolver')
				id = xbmcinit.read_addon(dir=addondir, full=True)
				sys.path.extend([p for p in _info[id]['_pythonpath'] if p not in sys.path])
				sys.stderr.write('%s version %s\n' % (_info[id]['name'], _info[id]['version']))
				self.urlresolver = True
			except:
				sys.stderr.write('unable to start xbmc-urlresolver\n')
				traceback.print_exc()

	def start(self):
		self.started = True
		if self.urlresolver:
			try:
				argv = sys.argv
				sys.argv = [sys.argv[0], '?']
				loglevel = self.setloglevel(xbmc.LOGINFO)
				import urlresolver
				self.setloglevel(loglevel)
				__builtin__.urlresolver = urlresolver
				sys.argv = argv
				self.started = True
			except:
				self.urlresolver = False
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
				u = (argv[2] if len(argv) > 2 and argv[2].startswith('plugin://') else None)
		elif self.urlresolver:
			loglevel = self.setloglevel(xbmc.LOGINFO)
			u = urlresolver.resolve(url)
			self.setloglevel(loglevel)
		if u:
			if 'plugin://' in u:
				pmsaddItem = __builtin__.pms.addItem
				pmsutil = __builtin__.pms.util
				def addItem(itemtype, name, argv, thumb=None, details=None, data=None):
					if type(argv).__name__ == 'list':
						argv = pms.flatten(argv)
					resolver.add(argv, name, thumb, details=details)
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
				details = {}
				url,headers = xbmc.split_url_headers(u)
				if headers:
					details['headers'] = headers
				resolver.add(url, details=details)

	def setloglevel(self, level):
		# backward compatibility
		try: return xbmc.setloglevel(level)
		except: return 0


# only resolves 'plugin://' urls
class _xbmc(_xbmc_urlresolver):
	def __init__(self):
		_xbmc_urlresolver.__init__(self, False)


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
					sys.stderr.write('youtube-dl version %s\n' % youtube_dl.version.__version__)
					scrapers.append(self)
					return
				except: traceback.print_exc()
		sys.stderr.write('unable to open youtube-dl\n')
		if self in scrapers: scrapers.remove(self)

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
		# v2015.04.26+: explicitly setting '-f best' to avoid new 'merged format' separate a/v urls
		youtube_dl.main(['-s', '--no-playlist', '-f', 'best', url])
		sys.stdout, sys.stderr, sys.exit = stdout, stderr, exit

		if len(self.info_dicts) > 0:
			# get the first result
			i = self.info_dicts[0]
			if i.get('url') is None and i.get('requested_formats') is not None:
				# v 2014.12.10+ (shouldn't happen, but just in case)
				req = info_dict.get('requested_formats')
				if len(req) == 1:
					i = req[0]
				else:
					raise 'Error: multiple "merged format" urls received'
#			from pprint import pprint
#			pprint(i)
			details = {}
			if 'http_headers' in i:
				headers = {k:v for k,v in i['http_headers'].items() if not k.lower().startswith('accept')}
				if headers:
					details['headers'] = headers
			resolver.add(i['url'] + i.get('play_path', ''), i.get('fulltitle'), i.get('thumbnail'),
				details=details if details else None)

#		# TODO: return all results (i.e. multi-part or playlist)
#		u = '\n'.join([(i['url'] + i.get('play_path', '')) for i in self.info_dicts])
#		if u:
#			i = self.info_dicts[0]
#			resolver.add(u, i.get('fulltitle'), i.get('thumbnail'))


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
		info.update({k.name():[str(scrapers.index(k)+1), 'priority (1=first|2=second|-1=disable)'] for k in scrapers if not k.name() == 'xbmc'})
		settings = pms.setInfo(info, '.resolver')
#		sys.stderr.write('settings: %s'%settings)
		resolvers = [k.name() for k in scrapers]
#		sys.stderr.write('resolvers: %s'%resolvers)
		s = {k:v for k,v in settings.items() if k in resolvers and v.isdigit()}
		if 'xbmc' in resolvers:
			s['xbmc'] = '3'
		pms.setVar('_resolvers', ' '.join(sorted(s, key=s.get)))

