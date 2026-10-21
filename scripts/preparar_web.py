#!/usr/bin/env python3
"""Bundle the HTML and add Android file/back integration without changing the source."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'web/index.html'
if not source.is_file():
    sys.exit('Arquivo ausente: web/index.html. Coloque o HTML do aplicativo nesse caminho.')
html = source.read_text(encoding='utf-8')
helper = '''
  function enviarArquivoAndroid(file,compartilhar){
    if(!window.CamaraAndroid||typeof window.CamaraAndroid.writeFile!=='function')return false;
    if(file.size>15*1024*1024){toast('Arquivo maior que 15 MB.');return true;}
    var reader=new FileReader();
    reader.onload=function(){window.CamaraAndroid.writeFile(String(reader.result).split(',')[1],file.name||'arquivo.json',file.type||'application/octet-stream',!!compartilhar);};
    reader.onerror=function(){toast('Não foi possível preparar o arquivo.');};
    reader.readAsDataURL(file);return true;
  }
'''
marker = '  function downloadFile(file){'
if html.count(marker) != 1:
    sys.exit('Não foi possível localizar downloadFile no HTML. A integração precisa ser revisada.')
html = html.replace(marker, helper + marker + "\n    if(enviarArquivoAndroid(file,false))return;", 1)
share_marker = "    var file=new File(['\\ufeff'+textValue],name,{type:'text/plain;charset=utf-8'});"
if html.count(share_marker) != 1:
    sys.exit('Não foi possível localizar o compartilhamento TXT no HTML.')
html = html.replace(share_marker, share_marker + '\n    if(enviarArquivoAndroid(file,true))return;', 1)
export_lines = [line for line in html.splitlines() if 'var file=new File([JSON.stringify(payload' in line]
if len(export_lines) != 1:
    sys.exit('Não foi possível localizar a exportação JSON no HTML.')
html = html.replace(export_lines[0], export_lines[0] + '\n    if(enviarArquivoAndroid(file,false))return;', 1)
# This is inside the app closure so it can follow the same navigation as its buttons.
back = '''
  window.CamaraNavegacaoVoltar=function(){
    if(!sheetWrap.hidden){closeSheet();return true;}
    if(registrationStage==='catalog'){showRegistration('gateway');return true;}
    if(choosingDay){showRegistration('gateway');return true;}
    if(registrationStage==='gateway'){registrationStage=null;booting=true;choosingDay=false;render();return true;}
    if(!booting){openLoadPicker();return true;}
    return false;
  };
'''
end = '  render();\n})();'
if html.count(end) != 1:
    sys.exit('Não foi possível localizar o final do aplicativo para integrar o botão Voltar.')
html = html.replace(end, back + end, 1)
output = ROOT / 'app/src/main/assets/index.html'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(html, encoding='utf-8')
print('HTML preparado para Android: app/src/main/assets/index.html')
