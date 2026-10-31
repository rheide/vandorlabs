"""Shared accessible page shell for catalog-driven texture comparisons."""
from pathlib import Path
import re

def render_page(**values):
    page=(Path(__file__).parent/'texture_matrix.html').read_text()
    for key,value in values.items():page=page.replace('<!--'+key.upper()+'-->',value)
    if re.search(r'<!--[A-Z_]+-->',page):raise ValueError('Unfilled texture matrix template field')
    return page.encode()
