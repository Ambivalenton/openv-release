# SPDX-License-Identifier: GPL-3.0-or-later
def balanced(text):
    pos=0
    def code(end=None):
        nonlocal pos
        stack=[]
        while pos<len(text):
            if text.startswith('//',pos):
                n=text.find('\n',pos);pos=len(text) if n<0 else n+1;continue
            if text.startswith('/*',pos):
                depth=1;pos+=2
                while depth and pos<len(text):
                    if text.startswith('/*',pos):depth+=1;pos+=2
                    elif text.startswith('*/',pos):depth-=1;pos+=2
                    else:pos+=1
                assert depth==0,'unclosed comment'
                continue
            c=text[pos]
            if c in ['"',"'"]:
                token='"""' if text.startswith('"""',pos) else c
                pos+=len(token)
                while pos<len(text):
                    if text.startswith(token,pos):pos+=len(token);break
                    if token=='"' and text[pos]=='\\':pos+=2;continue
                    if token=="'" and text[pos]=='\\':pos+=2;continue
                    if token!="'" and text.startswith('${',pos):pos+=2;code('}');continue
                    pos+=1
                else:raise AssertionError('unclosed string')
                continue
            if c==end and not stack:pos+=1;return
            if c in '([{':stack.append(c)
            elif c in ')]}':
                assert stack and stack.pop()=={')':'(',']':'[','}':'{'}[c],f'unmatched {c} at {pos}'
            pos+=1
        assert not stack and end is None,'unclosed delimiter'
    code()
