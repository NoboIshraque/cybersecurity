"""Render docs/submission/Mirage_Presentation_Scripts.md as the speakers' PDF (one part per page).

usage: python tools/docs/scripts_pdf.py   (from the repository root; needs reportlab and DejaVu fonts)
"""
import re

from reportlab.lib import colors
from reportlab.lib.fonts import addMapping
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

SRC = "docs/submission/Mirage_Presentation_Scripts.md"
OUT = "docs/submission/Mirage_Presentation_Scripts.pdf"
FONTS = "/usr/share/fonts/truetype/dejavu/"
pdfmetrics.registerFont(TTFont("DV", FONTS + "DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DVB", FONTS + "DejaVuSans-Bold.ttf"))
addMapping("DV", 0, 0, "DV")
addMapping("DV", 1, 0, "DVB")

TEAL, GREY, ORANGE = colors.HexColor("#1F7A70"), colors.HexColor("#555555"), colors.HexColor("#B5700F")
h1 = ParagraphStyle("h1", fontName="DVB", fontSize=19, leading=24, spaceAfter=2)
h2 = ParagraphStyle("h2", fontName="DVB", fontSize=13, leading=17, textColor=ORANGE, spaceAfter=4)
sub = ParagraphStyle("sub", fontName="DV", fontSize=10, leading=13.5, textColor=GREY)
cell = ParagraphStyle("cell", fontName="DV", fontSize=9, leading=11.5, textColor=GREY)
num = ParagraphStyle("num", fontName="DVB", fontSize=15, leading=18, textColor=TEAL)
say = ParagraphStyle("say", fontName="DV", fontSize=13, leading=18)


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;")


def parts(md):
    for block in md.split("\n## ")[1:]:
        head, rest = block.split("\n", 1)
        title, who = head.rsplit(" — ", 1)
        meta = rest.strip().split("\n", 1)[0]
        rows = re.findall(r"\*\*Slide (\d+) — (.*?)\*\*  \n(.*?)\n", rest)
        yield title, who, meta, rows


def main():
    story = []
    for i, (title, who, meta, rows) in enumerate(parts(open(SRC, encoding="utf-8").read())):
        if i:
            story.append(PageBreak())
        note = " Paced for a slow speaker." if who == "Nobo" else ""
        story += [Paragraph(esc(title), h1), Paragraph("Speaker: " + who, h2),
                  Paragraph(esc(meta) + " at an easy pace." + note
                            + " Small grey text = the slide on screen; large text = what to say.", sub),
                  Spacer(1, 8)]
        data = []
        for n, slide, line in rows:
            text = re.sub(r"\[(.*?)\]", lambda m: f'<font size="10" color="#B5700F">[{m.group(1)}]</font>', esc(line))
            data.append([Paragraph(n, num), [Paragraph(esc(slide), cell), Paragraph(text, say)]])
        table = Table(data, colWidths=[14 * mm, 166 * mm])
        table.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"),
                                   ("LINEBELOW", (0, 0), (-1, -1), 0.4, colors.HexColor("#DDDDDD")),
                                   ("TOPPADDING", (0, 0), (-1, -1), 6), ("BOTTOMPADDING", (0, 0), (-1, -1), 7)]))
        story.append(table)

    def footer(c, d):
        c.setFont("DV", 8)
        c.setFillColor(GREY)
        c.drawString(15 * mm, 10 * mm, "Mirage · ICE-2206 · speaker scripts")
        c.drawRightString(A4[0] - 15 * mm, 10 * mm, f"Page {d.page}")

    SimpleDocTemplate(OUT, pagesize=A4, leftMargin=15 * mm, rightMargin=15 * mm, topMargin=15 * mm,
                      bottomMargin=16 * mm, title="Mirage presentation scripts").build(
        story, onFirstPage=footer, onLaterPages=footer)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
