#
# Copyright (C) 2026 Philip Helger (www.helger.com)
# philip[at]helger[dot]com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

my @file    = glob "version1/*.crepdl";
for (my $i = 0; $i <= $#file; $i++){
    my $infilename = $file[$i];
    print $infilename, "\n";
    open (INFILE,  "< $infilename") or die("Error");
    my $outfilename = $infilename;
    $outfilename =~ s/version1/version2/g;
    print $outfilename, "\n";
    open (OUTFILE, ">> $outfilename") or die("Error");
    while (my $line = <INFILE>)  {
	$line =~ s/1.0/2.0\" mode=\"character/g;
	print OUTFILE $line;
    }
    close OUTFILE;
    close INFILE;
}
