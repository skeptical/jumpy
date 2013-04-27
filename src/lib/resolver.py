################# configuration: use True/False #################
enable_xbmc = True
enable_youtube_dl = True
#################################################################

import os, sys, traceback, __builtin__
from cStringIO import StringIO
import jumpy

class resolver:

	class Java:
		implements = ['net.pms.external.infidel.jumpy.resolver$Resolver']

	def resolve(self, url):
#		print 'resolving %s' % url
		u = None
		if have_xbmc:
			name = 'xbmc'
			u = self.xbmc(url)
		if not u and have_youtube_dl:
			name = 'youtube-dl'
			u = self.youtube_dl(url)
		if u:
			pms.log('%s url resolver: %s' % (name, u))
		return u

	def xbmc(self, url):
		u = None
		if 'plugin://' in url:
			if url.startswith('plugin://'):
				u = url
			elif url.startswith('['):
				argv = url[1:-1].split(' , ')
				u = (argv[1] if len(argv) > 1 and argv[1].startswith('plugin://') else None)
		elif have_urlresolver:
			# note: we might get a plugin url here
			u = urlresolver.resolve(url)
		if u and 'plugin://' in u:
			self.item = None
			xbmcinit.run_addon(u)
			u = self.item
		return (u.strip() if u else None)

	def youtube_dl(self, url):
		stdout, stderr, exit = sys.stdout, sys.stderr, sys.exit
		# prevent youtube_dl from shutting us down, discard stderr
		sys.exit, sys.stderr = noexit, devnull
		# youtube_dl prints out the result
		sys.stdout = capture = StringIO()
		youtube_dl.main(['-g', url])
		sys.stdout, sys.stderr, sys.exit = stdout, stderr, exit
		u = capture.getvalue()
		return (u.strip() if u else None)

	def addItem(self, itemtype, name, argv, thumb = None, data = None):
		self.item = argv

	def __init__(self):
		try:
			pms.register(self)
		except: traceback.print_exc()
		__builtin__.pms.addItem = self.addItem


start = (False if 'validate' in sys.argv else True)
have_xbmc = have_urlresolver = have_youtube_dl = False
home = pms.getHome()

if enable_xbmc:
	lib = os.path.join(home, 'xbmc')
	if os.path.exists(lib):
		sys.path.append(lib)
	try:
		import xbmcinit
		have_xbmc = xbmcinit.have_xbmc
		addondir = os.path.join(_special['home'], 'addons', 'script.module.urlresolver')
		id = xbmcinit.read_addon(dir=addondir, full=True)
		have_urlresolver = True
		sys.stderr.write('%s version %s\n' % (_info[id]['name'], _info[id]['version']))
		if start:
			sys.path.extend(_info[id]['_pythonpath'].split(os.path.pathsep))
			sys.argv = [sys.argv[0], '?']
			import urlresolver
	except:
		have_urlresolver = False
		sys.stderr.write('unable to open xbmc urlresolver\n')

if enable_youtube_dl:
	lib = pms.getProperty('youtube-dl.lib.path')
	if not lib:
		lib = pms.getProperty('youtube-dl.path')
	if lib:
		lib = os.path.join( \
			os.path.dirname(os.path.dirname(os.path.dirname(home))), \
			os.path.dirname(lib))
		if os.path.exists(os.path.join(lib, 'youtube_dl')):
			sys.path.append(lib)
	try:
		import youtube_dl
		sys.stderr.write('youtube-dl version %s\n' % youtube_dl.__version__)
		have_youtube_dl = True
		if start:
			# omit the last extractor (GenericIE, which takes forever)
			extractors = youtube_dl.gen_extractors()[:-1]
			def gen_extractors():
				return extractors
			youtube_dl.gen_extractors = gen_extractors
			# setup redirection
			devnull = open(os.devnull, 'w')
			def noexit(msg):
				pass
	except:
		have_youtube_dl = False
		sys.stderr.write('unable to open youtube-dl\n')

if start:
	resolver()

elif have_xbmc or have_youtube_dl:
	pms.setEnv('resolvers', '%s%s%s' % ( \
		'xbmc ' if have_xbmc else '', \
		'urlresolver ' if have_urlresolver else '', \
		'youtube-dl' if have_youtube_dl else ''))

