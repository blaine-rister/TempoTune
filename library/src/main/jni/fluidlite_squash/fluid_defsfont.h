/* FluidSynth - A Software Synthesizer
 *
 * Copyright (C) 2003  Peter Hanappe and others.
 *
 * SoundFont loading code borrowed from Smurf SoundFont Editor by Josh Green
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the Free
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307, USA
 */


#ifndef _FLUID_DEFSFONT_H
#define _FLUID_DEFSFONT_H


#include "fluidlite.h"
#include "fluidsynth_priv.h"
#include "fluid_list.h"



/********************************************************************************/
/********************************************************************************/
/********************************************************************************/
/********************************************************************************/
/********************************************************************************/

/*-----------------------------------sfont.h----------------------------*/

#define SF_SAMPMODES_LOOP	1
#define SF_SAMPMODES_UNROLL	2

#define SF_MIN_SAMPLERATE	400
#define SF_MAX_SAMPLERATE	50000

#define SF_MIN_SAMPLE_LENGTH	32

/* Sound Font structure defines */

typedef struct _SFVersion
{				/* version structure */
  uint16_t major;
  uint16_t minor;
}
SFVersion;

typedef struct _SFMod
{				/* Modulator structure */
  uint16_t src;			/* source modulator */
  uint16_t dest;			/* destination generator */
  int16_t amount;		/* signed, degree of modulation */
  uint16_t amtsrc;		/* second source controls amnt of first */
  uint16_t trans;		/* transform applied to source */
}
SFMod;

typedef union _SFGenAmount
{				/* Generator amount structure */
  int16_t sword;			/* signed 16 bit value */
  uint16_t uword;		/* unsigned 16 bit value */
  struct
  {
    uint8_t lo;			/* low value for ranges */
    uint8_t hi;			/* high value for ranges */
  }
  range;
}
SFGenAmount;

typedef struct _SFGen
{				/* Generator structure */
  uint16_t id;			/* generator ID */
  SFGenAmount amount;		/* generator value */
}
SFGen;

typedef struct _SFZone
{				/* Sample/instrument zone structure */
  fluid_list_t *instsamp;		/* instrument/sample pointer for zone */
  fluid_list_t *gen;			/* list of generators */
  fluid_list_t *mod;			/* list of modulators */
}
SFZone;

typedef struct _SFSample
{				/* Sample structure */
  char name[21];		/* Name of sample */
  uint8_t samfile;		/* Loaded sfont/sample buffer = 0/1 */
  uint32_t start;		/* Offset in sample area to start of sample */
  uint32_t end;			/* Offset from start to end of sample,
				   this is the last point of the
				   sample, the SF spec has this as the
				   1st point after, corrected on
				   load/save */
  uint32_t loopstart;		/* Offset from start to start of loop */
  uint32_t loopend;		/* Offset from start to end of loop,
				   marks the first point after loop,
				   whose sample value is ideally
				   equivalent to loopstart */
  uint32_t samplerate;		/* Sample rate recorded at */
  uint8_t origpitch;		/* root midi key number */
  int8_t pitchadj;		/* pitch correction in cents */
  uint16_t sampletype;		/* 1 mono,2 right,4 left,linked 8,0x8000=ROM */
}
SFSample;

typedef struct _SFInst
{				/* Instrument structure */
  char name[21];		/* Name of instrument */
  fluid_list_t *zone;			/* list of instrument zones */
}
SFInst;

typedef struct _SFPreset
{				/* Preset structure */
  char name[21];		/* preset name */
  uint16_t prenum;		/* preset number */
  uint16_t bank;			/* bank number */
  uint32_t libr;			/* Not used (preserved) */
  uint32_t genre;		/* Not used (preserved) */
  uint32_t morph;		/* Not used (preserved) */
  fluid_list_t *zone;			/* list of preset zones */
}
SFPreset;

/* NOTE: sffd is also used to determine if sound font is new (NULL) */
typedef struct _SFData
{				/* Sound font data structure */
  SFVersion version;		/* sound font version */
  SFVersion romver;		/* ROM version */
  unsigned int samplepos;		/* position within sffd of the sample chunk */
  unsigned int samplesize;		/* length within sffd of the sample chunk */
  char *fname;			/* file name */
  fluid_file sffd;			/* loaded sfont file descriptor */
  fluid_list_t *info;		     /* linked list of info strings (1st byte is ID) */
  fluid_list_t *preset;		/* linked list of preset info */
  fluid_list_t *inst;			/* linked list of instrument info */
  fluid_list_t *sample;		/* linked list of sample info */
}
SFData;

/* sf file chunk IDs */
enum
{ UNKN_ID, RIFF_ID, LIST_ID, SFBK_ID,
  INFO_ID, SDTA_ID, PDTA_ID,	/* info/sample/preset */

  IFIL_ID, ISNG_ID, INAM_ID, IROM_ID, /* info ids (1st byte of info strings) */
  IVER_ID, ICRD_ID, IENG_ID, IPRD_ID,	/* more info ids */
  ICOP_ID, ICMT_ID, ISFT_ID,	/* and yet more info ids */

  SNAM_ID, SMPL_ID,		/* sample ids */
  PHDR_ID, PBAG_ID, PMOD_ID, PGEN_ID,	/* preset ids */
  IHDR_ID, IBAG_ID, IMOD_ID, IGEN_ID,	/* instrument ids */
  SHDR_ID			/* sample info */
};

/* generator types */
typedef enum
{ Gen_StartAddrOfs, Gen_EndAddrOfs, Gen_StartLoopAddrOfs,
  Gen_EndLoopAddrOfs, Gen_StartAddrCoarseOfs, Gen_ModLFO2Pitch,
  Gen_VibLFO2Pitch, Gen_ModEnv2Pitch, Gen_FilterFc, Gen_FilterQ,
  Gen_ModLFO2FilterFc, Gen_ModEnv2FilterFc, Gen_EndAddrCoarseOfs,
  Gen_ModLFO2Vol, Gen_Unused1, Gen_ChorusSend, Gen_ReverbSend, Gen_Pan,
  Gen_Unused2, Gen_Unused3, Gen_Unused4,
  Gen_ModLFODelay, Gen_ModLFOFreq, Gen_VibLFODelay, Gen_VibLFOFreq,
  Gen_ModEnvDelay, Gen_ModEnvAttack, Gen_ModEnvHold, Gen_ModEnvDecay,
  Gen_ModEnvSustain, Gen_ModEnvRelease, Gen_Key2ModEnvHold,
  Gen_Key2ModEnvDecay, Gen_VolEnvDelay, Gen_VolEnvAttack,
  Gen_VolEnvHold, Gen_VolEnvDecay, Gen_VolEnvSustain, Gen_VolEnvRelease,
  Gen_Key2VolEnvHold, Gen_Key2VolEnvDecay, Gen_Instrument,
  Gen_Reserved1, Gen_KeyRange, Gen_VelRange,
  Gen_StartLoopAddrCoarseOfs, Gen_Keynum, Gen_Velocity,
  Gen_Attenuation, Gen_Reserved2, Gen_EndLoopAddrCoarseOfs,
  Gen_CoarseTune, Gen_FineTune, Gen_SampleId, Gen_SampleModes,
  Gen_Reserved3, Gen_ScaleTune, Gen_ExclusiveClass, Gen_OverrideRootKey,
  Gen_Dummy
}
Gen_Type;

#define Gen_MaxValid 	Gen_Dummy - 1	/* maximum valid generator */
#define Gen_Count	Gen_Dummy	/* count of generators */
#define GenArrSize sizeof(SFGenAmount)*Gen_Count	/* gen array size */

/* generator unit type */
typedef enum
{
  None,				/* No unit type */
  Unit_Smpls,			/* in samples */
  Unit_32kSmpls,		/* in 32k samples */
  Unit_Cent,			/* in cents (1/100th of a semitone) */
  Unit_HzCent,			/* in Hz Cents */
  Unit_TCent,			/* in Time Cents */
  Unit_cB,			/* in centibels (1/100th of a decibel) */
  Unit_Percent,			/* in percentage */
  Unit_Semitone,		/* in semitones */
  Unit_Range			/* a range of values */
}
Gen_Unit;

/* global data */

extern unsigned short badgen[]; 	/* list of bad generators */
extern unsigned short badpgen[]; 	/* list of bad preset generators */

/* functions */
void sfont_init_chunks (void);

void sfont_close (SFData * sf);
void sfont_free_zone (SFZone * zone);
int sfont_preset_compare_func (void* a, void* b);

void sfont_zone_delete (SFData * sf, fluid_list_t ** zlist, SFZone * zone);

fluid_list_t *gen_inlist (int gen, fluid_list_t * genlist);
int gen_valid (int gen);
int gen_validp (int gen);


/*-----------------------------------sffile.h----------------------------*/
/*
   File structures and routines (used to be in sffile.h)
*/

#define CHNKIDSTR(id)           &idlist[(id - 1) * 4]

/* sfont file chunk sizes */
#define SFPHDRSIZE	38
#define SFBAGSIZE	4
#define SFMODSIZE	10
#define SFGENSIZE	4
#define SFIHDRSIZE	22
#define SFSHDRSIZE	46

/* sfont file data structures */
typedef struct _SFChunk
{				/* RIFF file chunk structure */
  uint32_t id;			/* chunk id */
  uint32_t size;			/* size of the following chunk */
}
SFChunk;

typedef struct _SFPhdr
{
  unsigned char name[20];		/* preset name */
  uint16_t preset;		/* preset number */
  uint16_t bank;			/* bank number */
  uint16_t pbagndx;		/* index into preset bag */
  uint32_t library;		/* just for preserving them */
  uint32_t genre;		/* Not used */
  uint32_t morphology;		/* Not used */
}
SFPhdr;

typedef struct _SFBag
{
  uint16_t genndx;		/* index into generator list */
  uint16_t modndx;		/* index into modulator list */
}
SFBag;

typedef struct _SFIhdr
{
  char name[20];		/* Name of instrument */
  uint16_t ibagndx;		/* Instrument bag index */
}
SFIhdr;

typedef struct _SFShdr
{				/* Sample header loading struct */
  char name[20];		/* Sample name */
  uint32_t start;		/* Offset to start of sample */
  uint32_t end;			/* Offset to end of sample */
  uint32_t loopstart;		/* Offset to start of loop */
  uint32_t loopend;		/* Offset to end of loop */
  uint32_t samplerate;		/* Sample rate recorded at */
  uint8_t origpitch;		/* root midi key number */
  int8_t pitchadj;		/* pitch correction in cents */
  uint16_t samplelink;		/* Not used */
  uint16_t sampletype;		/* 1 mono,2 right,4 left,linked 8,0x8000=ROM */
}
SFShdr;

/* data */
extern char idlist[];

/* functions */
SFData *sfload_file (const char * fname);



/********************************************************************************/
/********************************************************************************/
/********************************************************************************/
/********************************************************************************/
/********************************************************************************/

/* GLIB - Library of useful routines for C programming
 * Copyright (C) 1995-1997  Peter Mattis, Spencer Kimball and Josh MacDonald
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */


/* Provide definitions for some commonly used macros.
 *  Some of them are only provided if they haven't already
 *  been defined. It is assumed that if they are already
 *  defined then the current definition is correct.
 */
#ifndef	FALSE
#define	FALSE	(0)
#endif

#ifndef	TRUE
#define	TRUE	(!FALSE)
#endif

#define GPOINTER_TO_INT(p)	((intptr_t)   (p))
#define GINT_TO_POINTER(i)      ((void *)  ((intptr_t) (i)))

char*	 g_strdup		(const char *str);





/* Provide simple macro statement wrappers (adapted from Perl):
 *  G_STMT_START { statements; } G_STMT_END;
 *  can be used as a single statement, as in
 *  if (x) G_STMT_START { ... } G_STMT_END; else ...
 *
 *  For gcc we will wrap the statements within `({' and `})' braces.
 *  For SunOS they will be wrapped within `if (1)' and `else (void) 0',
 *  and otherwise within `do' and `while (0)'.
 */
#if !(defined (G_STMT_START) && defined (G_STMT_END))
#  if defined (__GNUC__) && !defined (__STRICT_ANSI__) && !defined (__cplusplus)
#    define G_STMT_START	(void)(
#    define G_STMT_END		)
#  else
#    if (defined (sun) || defined (__sun__))
#      define G_STMT_START	if (1)
#      define G_STMT_END	else (void)0
#    else
#      define G_STMT_START	do
#      define G_STMT_END	while (0)
#    endif
#  endif
#endif


/* Basic bit swapping functions
 */
#define GUINT16_SWAP_LE_BE_CONSTANT(val)	((uint16_t) ( \
    (((uint16_t) (val) & (uint16_t) 0x00ffU) << 8) | \
    (((uint16_t) (val) & (uint16_t) 0xff00U) >> 8)))
#define GUINT32_SWAP_LE_BE_CONSTANT(val)	((uint32_t) ( \
    (((uint32_t) (val) & (uint32_t) 0x000000ffU) << 24) | \
    (((uint32_t) (val) & (uint32_t) 0x0000ff00U) <<  8) | \
    (((uint32_t) (val) & (uint32_t) 0x00ff0000U) >>  8) | \
    (((uint32_t) (val) & (uint32_t) 0xff000000U) >> 24)))

#define GUINT16_SWAP_LE_BE(val) (GUINT16_SWAP_LE_BE_CONSTANT (val))
#define GUINT32_SWAP_LE_BE(val) (GUINT32_SWAP_LE_BE_CONSTANT (val))

#define GINT16_TO_LE(val)	((int16_t) (val))
#define GUINT16_TO_LE(val)	((uint16_t) (val))
#define GINT16_TO_BE(val)	((int16_t) GUINT16_SWAP_LE_BE (val))
#define GUINT16_TO_BE(val)	(GUINT16_SWAP_LE_BE (val))
#define GINT32_TO_LE(val)	((int32_t) (val))
#define GUINT32_TO_LE(val)	((uint32_t) (val))
#define GINT32_TO_BE(val)	((int32_t) GUINT32_SWAP_LE_BE (val))
#define GUINT32_TO_BE(val)	(GUINT32_SWAP_LE_BE (val))

/* The G*_TO_?E() macros are defined in glibconfig.h.
 * The transformation is symmetric, so the FROM just maps to the TO.
 */
#define GINT16_FROM_LE(val)	(GINT16_TO_LE (val))
#define GUINT16_FROM_LE(val)	(GUINT16_TO_LE (val))
#define GINT16_FROM_BE(val)	(GINT16_TO_BE (val))
#define GUINT16_FROM_BE(val)	(GUINT16_TO_BE (val))
#define GINT32_FROM_LE(val)	(GINT32_TO_LE (val))
#define GUINT32_FROM_LE(val)	(GUINT32_TO_LE (val))
#define GINT32_FROM_BE(val)	(GINT32_TO_BE (val))
#define GUINT32_FROM_BE(val)	(GUINT32_TO_BE (val))


/*-----------------------------------util.h----------------------------*/
/*
  Utility functions (formerly in util.h)
 */
#define FAIL	0
#define OK	1

enum
{ ErrWarn, ErrFatal, ErrStatus, ErrCorr, ErrEof, ErrMem, Errno,
  ErrRead, ErrWrite
};

#define ErrMax		ErrWrite
#define ErrnoStart	Errno
#define ErrnoEnd	ErrWrite

int gerr (int ev, char * fmt, ...);
int safe_fread (void *buf, int count, fluid_file fd);
int safe_fwrite (void *buf, int count, fluid_file fd);
int safe_fseek (fluid_file fd, long ofs, int whence);


/********************************************************************************/
/********************************************************************************/
/********************************************************************************/
/********************************************************************************/
/********************************************************************************/



/***************************************************************
 *
 *       FORWARD DECLARATIONS
 */
typedef struct _fluid_defsfont_t fluid_defsfont_t;
typedef struct _fluid_defpreset_t fluid_defpreset_t;
typedef struct _fluid_preset_zone_t fluid_preset_zone_t;
typedef struct _fluid_inst_t fluid_inst_t;
typedef struct _fluid_inst_zone_t fluid_inst_zone_t;

/*

  Public interface

 */

fluid_sfloader_t* new_fluid_defsfloader(void);
int delete_fluid_defsfloader(fluid_sfloader_t* loader);
fluid_sfont_t* fluid_defsfloader_load(fluid_sfloader_t* loader, const char* filename);


int fluid_defsfont_sfont_delete(fluid_sfont_t* sfont);
char* fluid_defsfont_sfont_get_name(fluid_sfont_t* sfont);
fluid_preset_t* fluid_defsfont_sfont_get_preset(fluid_sfont_t* sfont, unsigned int bank, unsigned int prenum);
void fluid_defsfont_sfont_iteration_start(fluid_sfont_t* sfont);
int fluid_defsfont_sfont_iteration_next(fluid_sfont_t* sfont, fluid_preset_t* preset);


int fluid_defpreset_preset_delete(fluid_preset_t* preset);
char* fluid_defpreset_preset_get_name(fluid_preset_t* preset);
int fluid_defpreset_preset_get_banknum(fluid_preset_t* preset);
int fluid_defpreset_preset_get_num(fluid_preset_t* preset);
void fluid_defpreset_preset_get_range(const fluid_preset_t *const preset, uint8_t *const range);
int fluid_defpreset_preset_noteon(fluid_preset_t* preset, fluid_synth_t* synth, int chan, int key, int vel);


/*
 * fluid_defsfont_t
 */
struct _fluid_defsfont_t
{
  char* filename;           /* the filename of this soundfont */
  unsigned int samplepos;   /* the position in the file at which the sample data starts */
  unsigned int samplesize;  /* the size of the sample data */
  short* sampledata;        /* the sample data, loaded in ram */
  fluid_list_t* sample;      /* the samples in this soundfont */
  fluid_defpreset_t* preset; /* the presets of this soundfont */

  fluid_preset_t iter_preset;        /* preset interface used in the iteration */
  fluid_defpreset_t* iter_cur;       /* the current preset in the iteration */
};


fluid_defsfont_t* new_fluid_defsfont(void);
int delete_fluid_defsfont(fluid_defsfont_t* sfont);
int fluid_defsfont_load(fluid_defsfont_t* sfont, const char* file);
char* fluid_defsfont_get_name(fluid_defsfont_t* sfont);
fluid_defpreset_t* fluid_defsfont_get_preset(fluid_defsfont_t* sfont, unsigned int bank, unsigned int prenum);
void fluid_defsfont_iteration_start(fluid_defsfont_t* sfont);
int fluid_defsfont_iteration_next(fluid_defsfont_t* sfont, fluid_preset_t* preset);
int fluid_defsfont_load_sampledata(fluid_defsfont_t* sfont);
int fluid_defsfont_add_sample(fluid_defsfont_t* sfont, fluid_sample_t* sample);
int fluid_defsfont_add_preset(fluid_defsfont_t* sfont, fluid_defpreset_t* preset);
fluid_sample_t* fluid_defsfont_get_sample(fluid_defsfont_t* sfont, char *s);


/*
 * fluid_preset_t
 */
struct _fluid_defpreset_t
{
  fluid_defpreset_t* next;
  fluid_defsfont_t* sfont;                  /* the soundfont this preset belongs to */
  char name[21];                        /* the name of the preset */
  unsigned int bank;                    /* the bank number */
  unsigned int num;                     /* the preset number */
  fluid_preset_zone_t* global_zone;        /* the global zone of the preset */
  fluid_preset_zone_t* zone;               /* the chained list of preset zones */
};

fluid_defpreset_t* new_fluid_defpreset(fluid_defsfont_t* sfont);
int delete_fluid_defpreset(fluid_defpreset_t* preset);
fluid_defpreset_t* fluid_defpreset_next(fluid_defpreset_t* preset);
int fluid_defpreset_import_sfont(fluid_defpreset_t* preset, SFPreset* sfpreset, fluid_defsfont_t* sfont);
int fluid_defpreset_set_global_zone(fluid_defpreset_t* preset, fluid_preset_zone_t* zone);
int fluid_defpreset_add_zone(fluid_defpreset_t* preset, fluid_preset_zone_t* zone);
fluid_preset_zone_t* fluid_defpreset_get_zone(fluid_defpreset_t* preset);
fluid_preset_zone_t* fluid_defpreset_get_global_zone(fluid_defpreset_t* preset);
int fluid_defpreset_get_banknum(fluid_defpreset_t* preset);
int fluid_defpreset_get_num(fluid_defpreset_t* preset);
char* fluid_defpreset_get_name(fluid_defpreset_t* preset);
void fluid_defpreset_get_range(const fluid_defpreset_t *const preset, uint8_t *const range);
int fluid_defpreset_noteon(fluid_defpreset_t* preset, fluid_synth_t* synth, int chan, int key, int vel);

/*
 * fluid_preset_zone
 */
struct _fluid_preset_zone_t
{
  fluid_preset_zone_t* next;
  char* name;
  fluid_inst_t* inst;
  int keylo;
  int keyhi;
  int vello;
  int velhi;
  fluid_gen_t gen[GEN_LAST];
  fluid_mod_t * mod; /* List of modulators */
};

fluid_preset_zone_t* new_fluid_preset_zone(char* name);
int delete_fluid_preset_zone(fluid_preset_zone_t* zone);
fluid_preset_zone_t* fluid_preset_zone_next(fluid_preset_zone_t* preset);
int fluid_preset_zone_import_sfont(fluid_preset_zone_t* zone, SFZone* sfzone, fluid_defsfont_t* sfont);
int fluid_preset_zone_inside_range(fluid_preset_zone_t* zone, int key, int vel);
fluid_inst_t* fluid_preset_zone_get_inst(fluid_preset_zone_t* zone);

/*
 * fluid_inst_t
 */
struct _fluid_inst_t
{
  char name[21];
  fluid_inst_zone_t* global_zone;
  fluid_inst_zone_t* zone;
};

fluid_inst_t* new_fluid_inst(void);
int delete_fluid_inst(fluid_inst_t* inst);
int fluid_inst_import_sfont(fluid_inst_t* inst, SFInst *sfinst, fluid_defsfont_t* sfont);
int fluid_inst_set_global_zone(fluid_inst_t* inst, fluid_inst_zone_t* zone);
int fluid_inst_add_zone(fluid_inst_t* inst, fluid_inst_zone_t* zone);
fluid_inst_zone_t* fluid_inst_get_zone(fluid_inst_t* inst);
fluid_inst_zone_t* fluid_inst_get_global_zone(fluid_inst_t* inst);

/*
 * fluid_inst_zone_t
 */
struct _fluid_inst_zone_t
{
  fluid_inst_zone_t* next;
  char* name;
  fluid_sample_t* sample;
  int keylo;
  int keyhi;
  int vello;
  int velhi;
  fluid_gen_t gen[GEN_LAST];
  fluid_mod_t * mod; /* List of modulators */
};

fluid_inst_zone_t* new_fluid_inst_zone(char* name);
int delete_fluid_inst_zone(fluid_inst_zone_t* zone);
fluid_inst_zone_t* fluid_inst_zone_next(fluid_inst_zone_t* zone);
int fluid_inst_zone_import_sfont(fluid_inst_zone_t* zone, SFZone *sfzone, fluid_defsfont_t* sfont);
int fluid_inst_zone_inside_range(fluid_inst_zone_t* zone, int key, int vel);
fluid_sample_t* fluid_inst_zone_get_sample(fluid_inst_zone_t* zone);



fluid_sample_t* new_fluid_sample(void);
int delete_fluid_sample(fluid_sample_t* sample);
int fluid_sample_import_sfont(fluid_sample_t* sample, SFSample* sfsample, fluid_defsfont_t* sfont);
int fluid_sample_in_rom(fluid_sample_t* sample);


#endif  /* _FLUID_SFONT_H */
