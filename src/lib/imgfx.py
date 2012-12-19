#!/bin/python

import sys, os, re

def escape(s):
	if sys.platform.startswith('win32'):
		return s.replace('%', '%%')
	else:
		return s.replace('(', '\(').replace(')', '\)').replace('!', '\!')

def render(img, fx = None, outdir = '.', fill = None, resize = None, srt = None,
		crop = None, flip = False, flop = False, z = False, shadow = False, gel = False):

	if fx is not None:
		for opt in fx.split(';'):
			x = opt.split('=', 1)
			if   x[0] == 'f': fill = x[1]
			elif x[0] == 'r': resize = x[1]
			elif x[0] == 't': srt = x[1]
			elif x[0] == 'c': crop = x[1]
			elif x[0] == '3': z = True
			elif x[0] == 's': shadow = True
			elif x[0] == 'g': gel = True
			elif x[0] == 'v': flip = True
			elif x[0] == 'h': flop = True
		tag = fx
	else:
		tag = ('%s%s%s%s%s%s%s%s%s' % ( \
			'f=%s;' % fill if fill else '', 'r=%s;' % resize if resize else '', \
			't=%s;' % srt if srt else '', 'c=%s;' % crop if crop else '', \
			'3;' if z else '', 'g;' if gel else '', 's;' if shadow else '', \
			'v;' if flip else '', 'h' if flop else '') ).rstrip(';')

	name, ext = os.path.splitext(os.path.basename(img))
	out = '%s+%s%s' % (os.path.join(outdir, name), tag, ext)

	if os.path.exists(out):
		print 'already exists: \'%s\'' % (out)
		sys.exit(0)

	imconvert = os.getenv('imconvert') if 'imconvert' in os.environ else 'convert'
	convert = [imconvert, '"%s"' % img]

	# http://www.imagemagick.org/Usage/warping/#flip
	# http://www.imagemagick.org/Usage/distorts/#srt
	# http://www.imagemagick.org/Usage/advanced/#jigsaw
	# http://www.imagemagick.org/Usage/advanced/#gel_effects
	# http://www.imagemagick.org/Usage/transform/#shade

	if flip:
		convert.append( '-flip')
	if flop:
		convert.append( '-flop')
	if resize is not None:
		convert.extend(['-resize', resize])
	if srt is not None:
		convert.append( '-virtual-pixel transparent +distort SRT "%s" +repage' % srt.replace('_', ' '))
	if crop is not None:
		convert.append('-crop %s! -repage %s' % (crop, re.split('\+|-', crop)[0]))
	if fill is not None:
		convert.append('-alpha set -channel RGB -fill "%s" +opaque none' % fill)
	if z:
		convert.append(
			'( +clone -channel A -separate +channel -negate '
			'-background black -virtual-pixel background '
			'-blur 0x2 -shade 120x21.78 -contrast-stretch 0% '
			'+sigmoidal-contrast 7x50% -fill grey50 -colorize 10% '
			'+clone +swap -compose overlay -composite ) '
			'-compose In -composite'
		)
	if gel:
		convert.append(
			'( +clone -alpha extract -blur 0x12 -shade 110x0 -normalize '
			'-sigmoidal-contrast 16,60% -evaluate multiply .5 '
			'-roll +5+10 +clone -compose Screen -composite ) '
			'-compose In -composite '
			'( +clone -alpha extract -blur 0x2 -shade 0x90 -normalize '
			'-blur 0x2 +level 60,100% -alpha On ) '
			'-compose Multiply -composite'
		)
	if shadow:
		convert.append(
			'( +clone -background Black -shadow 50x3+4+4 ) '
			'-background none -compose DstOver -flatten'
		)

	convert.append('"%s"' % out)

	cmd = escape(' '.join(convert))
	sys.stderr.write("%s\n\n" % cmd)
	os.system(cmd)


if __name__ == "__main__":

	import getopt

	def usage(err=None):
		if err: sys.stderr.write('%s\n' % err)
		sys.stderr.write( """
usage:  imgfx [-i imagefile] [-x fxstring] [-f color]
		  [-r wxh] [-t SRT] [-c wxh] [-3] [-s] [-g] [-v] [-h]
		  [-o outputdir]

-x: shorthand fx string e.g. 'f.color:r.wxh:3:s' (overrides -f -r -t -c -3 -s -g -v -h)
-f: see imagemagick -fill (named color, #rrggbb etc)
-r: resize
-t: see imagemagick SRT (scale-rotate-transform)
-c: see imagemagick -crop
-s: apply shadow effect
-g: apply gel effect
-3: apply 3d effect
-v: flip vertical
-h: flip horizontal
"""		)
		sys.exit(1)

	try:
		opts = getopt.getopt(sys.argv[1:], "i:x:f:r:t:c:o:3sgvh")
	except getopt.GetoptError, err:
		usage(err)

	keys = {
		'-i' : 'img',
		'-o' : 'outdir',
		'-x' : 'fx',
		'-f' : 'fill',
		'-r' : 'resize',
		'-t' : 'srt',
		'-c' : 'crop',
#		'-b' : 'background',
		'-3' : 'z',
		'-s' : 'shadow',
		'-v' : 'flip',
		'-h' : 'flop',
	}

	kwargs = dict([(keys.get(k), v) for k, v in opts[0]])
#	# integers
#	for i in []:
#		if i in kwargs: kwargs[i] = int(kwargs[i])
	# booleans
	for i in ['z', 'gel', 'shadow', 'flip', 'flop']:
		if i in kwargs: kwargs[i] = True
	sys.stderr.write('args=%s\n' % kwargs)

	render(**kwargs)

