@echo off
set BRAINSTORM_DIR=C:\Users\13211\Desktop\myproject\agent-scope\.superpowers\brainstorm\70608-20260527231411
set BRAINSTORM_HOST=127.0.0.1
set BRAINSTORM_URL_HOST=localhost
cd /d C:\Users\13211\.codex\superpowers\skills\brainstorming\scripts
node server.cjs >> "C:\Users\13211\Desktop\myproject\agent-scope\.superpowers\brainstorm\70608-20260527231411\state\server.log" 2>> "C:\Users\13211\Desktop\myproject\agent-scope\.superpowers\brainstorm\70608-20260527231411\state\server.err"
