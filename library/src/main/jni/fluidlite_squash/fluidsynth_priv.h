/* FluidSynth - A Software Synthesizer
 *
 * Copyright (C) 2003  Peter Hanappe and others.
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


#ifndef _FLUIDSYNTH_PRIV_H
#define _FLUIDSYNTH_PRIV_H

#include "fluid_config.h"

#if HAVE_STRING_H
#include <string.h>
#endif

#if HAVE_STDLIB_H
#include <stdlib.h>
#endif

#if HAVE_STDIO_H
// Drop-in reimplementations for reading from Android assets
#ifdef FLUID_WITH_ANDROID_AASSET
#include <aasset_stdio_adapter.h>
#else
#include <stdio.h>

typedef FILE* fluid_file;

#define fluid_system_fopen fopen
#define fluid_system_fread fread
#define fluid_system_fseek fseek
#define fluid_system_fclose fclose
#define fluid_system_ftell ftell
#define fluid_system_rewing rewind

#endif /* ANDROID_ASSET */
#endif /* HAVE_STDIO_H */

#if HAVE_MATH_H
#include <math.h>
#endif

#if HAVE_STDARG_H
#include <stdarg.h>
#endif

#if HAVE_FCNTL_H
#include <fcntl.h>
#endif

#if HAVE_LIMITS_H
#include <limits.h>
#endif


#include "fluidlite.h"


/***************************************************************
 *
 *         BASIC TYPES
 */

#if defined(WITH_FLOAT)
typedef float fluid_real_t;
#else
typedef double fluid_real_t;
#endif


typedef enum {
  FLUID_OK = 0,
  FLUID_FAILED = -1
} fluid_status;


//socket disabled

/** Integer types  */

typedef signed char       sint8;
typedef unsigned char     uint8;
typedef signed short       sint16;
typedef unsigned short     uint16;
typedef signed int         sint32;
typedef unsigned int       uint32;

//#if defined(MINGW32)

///* Windows using MinGW32 */
//typedef int8_t             sint8;
//typedef uint8_t            uint8;
//typedef int16_t            sint16;
//typedef uint16_t           uint16;
//typedef int32_t            sint32;
//typedef uint32_t           uint32;
//typedef int64_t            sint64;
//typedef uint64_t           uint64;

//#elif defined(_WIN32)

///* Windows */
//typedef signed __int8      sint8;
//typedef unsigned __int8    uint8;
//typedef signed __int16     sint16;
//typedef unsigned __int16   uint16;
//typedef signed __int32     sint32;
//typedef unsigned __int32   uint32;
//typedef signed __int64     sint64;
//typedef unsigned __int64   uint64;

//#elif defined(MACOS9)

///* Macintosh */
//typedef signed char        sint8;
//typedef unsigned char      uint8;
//typedef signed short       sint16;
//typedef unsigned short     uint16;
//typedef signed int         sint32;
//typedef unsigned int       uint32;
///* FIXME: needs to be verified */
//typedef long long          sint64;
//typedef unsigned long long uint64;

//#else

///* Linux & Darwin */
//typedef int8_t             sint8;
//typedef u_int8_t           uint8;
//typedef int16_t            sint16;
//typedef u_int16_t          uint16;
//typedef int32_t            sint32;
//typedef u_int32_t          uint32;
//typedef int64_t            sint64;
//typedef u_int64_t          uint64;

//#endif


/***************************************************************
 *
 *       FORWARD DECLARATIONS
 */
typedef struct _fluid_env_data_t fluid_env_data_t;
typedef struct _fluid_adriver_definition_t fluid_adriver_definition_t;
typedef struct _fluid_channel_t fluid_channel_t;
typedef struct _fluid_tuning_t fluid_tuning_t;
typedef struct _fluid_hashtable_t  fluid_hashtable_t;
typedef struct _fluid_client_t fluid_client_t;

/***************************************************************
 *
 *                      CONSTANTS
 */

#define FLUID_BUFSIZE                64

#ifndef PI
#define PI                          3.141592654
#endif

/***************************************************************
 *
 *                      SYSTEM INTERFACE
 */

#define FLUID_MALLOC(_n)             malloc(_n)
#define FLUID_REALLOC(_p,_n)         realloc(_p,_n)
#define FLUID_NEW(_t)                (_t*)malloc(sizeof(_t))
#define FLUID_ARRAY(_t,_n)           (_t*)malloc((_n)*sizeof(_t))
#define FLUID_FREE(_p)               free(_p)
#define FLUID_FOPEN(_f,_m)           fluid_system_fopen(_f,_m)
#define FLUID_FCLOSE(_f)             fluid_system_fclose(_f)
#define FLUID_FREAD(_p,_s,_n,_f)     fluid_system_fread(_p,_s,_n,_f)
#define FLUID_FSEEK(_f,_n,_set)      fluid_system_fseek(_f,_n,_set)
#define FLUID_FTELL(_f)              fluid_system_ftell(_f)
#define FLUID_REWIND(_f)             fluid_system_rewind(_f)
#define FLUID_MEMCPY(_dst,_src,_n)   memcpy(_dst,_src,_n)
#define FLUID_MEMSET(_s,_c,_n)       memset(_s,_c,_n)
#define FLUID_STRLEN(_s)             strlen(_s)
#define FLUID_STRCMP(_s,_t)          strcmp(_s,_t)
#define FLUID_STRNCMP(_s,_t,_n)      strncmp(_s,_t,_n)
#define FLUID_STRCPY(_dst,_src)      strcpy(_dst,_src)
#define FLUID_STRCHR(_s,_c)          strchr(_s,_c)
#ifdef strdup
#define FLUID_STRDUP(s)              strdup(s)
#else
#define FLUID_STRDUP(s) 		    FLUID_STRCPY(FLUID_MALLOC(FLUID_STRLEN(s) + 1), s)
#endif
#define FLUID_SPRINTF                sprintf
#define FLUID_FPRINTF                fprintf

#define fluid_clip(_val, _min, _max) \
{ (_val) = ((_val) < (_min))? (_min) : (((_val) > (_max))? (_max) : (_val)); }

#if WITH_FTS
#define FLUID_PRINTF                 post
#define FLUID_FLUSH()
#else
#define FLUID_PRINTF                 printf
#define FLUID_FLUSH()                fflush(stdout)
#endif

// Substitute Android log functions
#ifdef ANDROID_LOGGING
#include <android/log.h>
#define FLUID_LOG(type, ...) __android_log_print(ANDROID_LOG_ERROR, "", __VA_ARGS__)
#else
#define FLUID_LOG                    fluid_log
#endif

// Disable all logging for NDEBUG mode
#ifdef NDEBUG
#undef FLUID_LOG
#define FLUID_LOG(...) ;
#endif

#ifndef M_PI
#define M_PI 3.1415926535897932384626433832795
#endif


#define FLUID_ASSERT(a,b)
#define FLUID_ASSERT_P(a,b)

char* fluid_error(void);


/* Internationalization */
#define _(s) s


#endif /* _FLUIDSYNTH_PRIV_H */
