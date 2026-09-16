# Blows out vega

show all recruiting cancer trials, include cancer type and tested therapies
32 000 rows

show bargraph, of therapies, color by cancer type. 


# "sites in Northern California"

translates into this, which is not adequate (there are lots of other cities)
AND f.city ILIKE ANY (ARRAY['San Francisco', 'Oakland', 'San Jose', 'Sacramento', 'Berkeley', 'Santa Rosa', 'Fremont', 'Santa Clara', 'Sunnyvale']); 

includeing " (use zip)" in prompt is much batter, should be built-in

    AND f.state = 'California'
    AND (
        f.zip >= '94000' AND f.zip < '96100' -- assuming the zip codes for Northern California
    );
