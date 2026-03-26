param (
    [string]$inputFile,
    [int]$chunkSizeMB = 20
)

$chunkSize = $chunkSizeMB * 1MB
$reader = [System.IO.File]::OpenRead($inputFile)
$buffer = New-Object Byte[] $chunkSize
$index = 0

while ($reader.Position -lt $reader.Length) {
    $bytesRead = $reader.Read($buffer, 0, $buffer.Length)
    $outputFile = "$inputFile.part$index"
    $writer = [System.IO.File]::Create($outputFile)
    $writer.Write($buffer, 0, $bytesRead)
    $writer.Close()
    $index++
}

$reader.Close()