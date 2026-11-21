#!/usr/bin/env python3
"""Export per-character glyph coordinates for JVM parser regression tests.
Usage: python export_fixture.py INPUT.pdf OUTPUT.tsv
Requires: pip install pymupdf
This is a development-only fixture generator, never used by Android app.
"""
import fitz,sys
source,destination=sys.argv[1:3]
count=0
with open(destination,'w',encoding='utf-8',newline='\n') as dst:
    for page_index,page in enumerate(fitz.open(source),start=1):
        for block in page.get_text('rawdict')['blocks']:
            for line in block.get('lines',[]):
                for span in line['spans']:
                    for char in span['chars']:
                        symbol=char['c']
                        if '\t' in symbol or '\r' in symbol or '\n' in symbol:continue
                        x,y=char['origin'];x0,y0,x1,y1=char['bbox']
                        dst.write(f'{page_index}\t{symbol}\t{x:.3f}\t{y:.3f}\t{x1-x0:.3f}\t{y1-y0:.3f}\n')
                        count+=1
print(destination,count)
