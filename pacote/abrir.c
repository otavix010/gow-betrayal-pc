// GoW Betrayal.exe: abre o port no Windows (jre\bin\javaw.exe -jar gowb.jar, com os argumentos recebidos), na pasta
// dele, e espera o jogo fechar: quem o abriu (o Otavix Launcher, um atalho) vê o jogo rodando e o código de saída.
// O javaw não tem console: o que o jogo escreve vai para logs\log.txt (o da vez anterior fica em logs\log.1.txt).
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <stdio.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR args, int show) {
    (void)inst, (void)prev, (void)show;
    wchar_t dir[MAX_PATH];
    GetModuleFileNameW(NULL, dir, MAX_PATH);
    wchar_t* sep = wcsrchr(dir, L'\\');
    if (sep) *sep = 0;

    wchar_t logs[MAX_PATH + 16], log[MAX_PATH + 32], old[MAX_PATH + 32];
    _snwprintf(logs, MAX_PATH + 16, L"%ls\\logs", dir);
    _snwprintf(log, MAX_PATH + 32, L"%ls\\log.txt", logs);
    _snwprintf(old, MAX_PATH + 32, L"%ls\\log.1.txt", logs);
    CreateDirectoryW(logs, NULL);
    MoveFileExW(log, old, MOVEFILE_REPLACE_EXISTING);
    SECURITY_ATTRIBUTES sa = {sizeof sa, NULL, TRUE};
    HANDLE out = CreateFileW(log, GENERIC_WRITE, FILE_SHARE_READ, &sa, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);

    static wchar_t line[32768];
    _snwprintf(line, 32768, L"\"%ls\\jre\\bin\\javaw.exe\" -jar \"%ls\\gowb.jar\" %ls", dir, dir, args ? args : L"");
    STARTUPINFOW si = {sizeof si};
    if (out != INVALID_HANDLE_VALUE) {
        si.dwFlags = STARTF_USESTDHANDLES;
        si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
        si.hStdOutput = out;
        si.hStdError = out;
    }
    PROCESS_INFORMATION pi;
    if (!CreateProcessW(NULL, line, NULL, NULL, out != INVALID_HANDLE_VALUE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL, L"Não foi possível abrir o Java do port (pasta jre).", L"God of War: Betrayal", MB_ICONERROR);
        return 1;
    }
    if (out != INVALID_HANDLE_VALUE) CloseHandle(out);
    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 0;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return (int)code;
}
